package fr.ensim.asso;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les noms exposés au système de supervision.
 *
 * <p>Ce test ne vérifie pas que les compteurs sont incrémentés — c'est le rôle
 * de {@code DepotMediaTest} et des tests du portail. Il vérifie ce qu'une
 * alerte ou un tableau de bord devra écrire, c'est-à-dire la forme exacte que
 * prennent ces noms une fois traduits en Prometheus. Renommer une métrique
 * casse silencieusement une alerte, et une alerte cassée ne se manifeste que le
 * jour où elle aurait dû se déclencher.
 *
 * <p><strong>Et c'est précisément ce que ce test ne faisait pas.</strong> Chaque
 * cas enregistrait, dans son propre registre, un compteur dont il écrivait le
 * nom en toutes lettres, puis vérifiait la traduction Prometheus de ce nom-là.
 * Il éprouvait la convention de nommage de Micrometer — les points deviennent
 * des tirets bas, un compteur gagne un suffixe {@code _total} — et jamais les
 * noms du code de production. Renommer {@code ensimasso.portail.cache} dans le
 * service ne l'aurait pas fait broncher : exactement la panne silencieuse que
 * son propre javadoc annonce combattre.
 *
 * <p>Les noms sont donc désormais LUS dans les sources de production, et le cas
 * {@link #tousLesNomsSontProduits} confronte les deux listes. Le motif de
 * lecture attrape le nom OÙ QU'IL SOIT écrit — y compris quand l'appel est
 * réparti sur plusieurs lignes, ce qui est le cas de la jauge des mandats et
 * ce qui avait d'abord trompé ma propre recherche.
 */
class MetriquesTest {

    private final PrometheusMeterRegistry registre =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    /** Les métriques dont une alerte ou un tableau de bord dépend. */
    private static final List<String> ATTENDUES = List.of(
            "ensimasso.portail.cache",
            "ensimasso.portail.rendu",
            "ensimasso.media.depot",
            "ensimasso.tresorerie.webhook",
            "ensimasso.adhesion.calendrier_absent",
            "ensimasso.gouvernance.mandats.en_fonction");

    /** Tout nom de métrique littéral trouvé dans les sources de production. */
    private static Set<String> nomsDuCodeDeProduction() throws IOException {
        Path racine = Path.of("src/main/java");
        if (!Files.isDirectory(racine)) {
            racine = Path.of("v2/backend/src/main/java");
        }
        assertThat(racine).as("les sources de production doivent être lisibles").isDirectory();

        Pattern nom = Pattern.compile("\"(ensimasso\\.[A-Za-z0-9_.]+)\"");
        Set<String> trouves = new java.util.TreeSet<>();
        try (Stream<Path> flux = Files.walk(racine)) {
            for (Path f : flux.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = nom.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    trouves.add(m.group(1));
                }
            }
        }
        return trouves;
    }

    @Test
    @DisplayName("chaque métrique attendue est réellement produite par le code")
    void tousLesNomsSontProduits() throws IOException {
        Set<String> produits = nomsDuCodeDeProduction();

        List<String> absentes = ATTENDUES.stream().filter(n -> !produits.contains(n)).toList();

        // C'est le maillon qui manquait. Sans lui, renommer une métrique dans
        // le service laissait ce fichier au vert, et l'alerte qui en dépend
        // devenait muette — sans se manifester avant le jour où elle aurait dû
        // se déclencher. Vérifié en renommant ensimasso.portail.cache dans le
        // service : ce cas passe au rouge, les autres restent verts.
        assertThat(absentes)
                .as("ces métriques sont vérifiées ici mais aucune classe de "
                  + "production ne les publie")
                .isEmpty();
    }

    @Test
    @DisplayName("le cache du portail s'expose avec ses deux résultats")
    void cacheDuPortail() {
        registre.counter("ensimasso.portail.cache", "resultat", "succes").increment();
        registre.counter("ensimasso.portail.cache", "resultat", "defaut").increment(3);

        String expose = registre.scrape();
        assertThat(expose).contains("ensimasso_portail_cache_total{resultat=\"succes\"} 1.0");
        assertThat(expose).contains("ensimasso_portail_cache_total{resultat=\"defaut\"} 3.0");
    }

    @Test
    @DisplayName("les refus de dépôt s'exposent avec leur motif")
    void refusDeDepot() {
        registre.counter("ensimasso.media.depot", "resultat", "refuse", "motif", "svg").increment();

        // « ensimasso_media_depot_total{resultat="refuse",motif="svg"} » est ce
        // qu'une alerte devra écrire : le motif doit rester une étiquette, pas
        // finir dans le nom.
        assertThat(registre.scrape())
                .contains("ensimasso_media_depot_total")
                .contains("motif=\"svg\"")
                .contains("resultat=\"refuse\"");
    }

    @Test
    @DisplayName("les résultats de webhook s'exposent séparément")
    void webhooks() {
        for (String resultat : new String[] { "traite", "deja_traite", "ignore",
                                              "signature_invalide", "echec" }) {
            registre.counter("ensimasso.tresorerie.webhook", "resultat", resultat).increment();
        }
        String expose = registre.scrape();
        assertThat(expose).contains("resultat=\"signature_invalide\"");
        assertThat(expose).contains("resultat=\"echec\"");
    }

    @Test
    @DisplayName("le nombre de bureaux en fonction est une jauge, pas un compteur")
    void mandatsEnFonction() {
        AtomicLong valeur = new AtomicLong(3);
        io.micrometer.core.instrument.Gauge
                .builder("ensimasso.gouvernance.mandats.en_fonction", valeur::get)
                .register(registre);

        assertThat(registre.scrape())
                .contains("ensimasso_gouvernance_mandats_en_fonction 3.0");

        // Une jauge peut redescendre ; un compteur non. C'est la chute qui
        // intéresse ici : zéro bureau en fonction signifie qu'aucune page
        // publique ne s'affiche plus, et aucune métrique technique ne le dit.
        valeur.set(0);
        assertThat(registre.scrape())
                .contains("ensimasso_gouvernance_mandats_en_fonction 0.0");
    }

    @Test
    @DisplayName("la durée de rendu s'expose en secondes, avec ses quantiles")
    void dureeDeRendu() {
        io.micrometer.core.instrument.Timer.builder("ensimasso.portail.rendu")
                .publishPercentiles(0.5, 0.95)
                .register(registre)
                .record(java.time.Duration.ofMillis(42));

        String expose = registre.scrape();
        assertThat(expose).contains("ensimasso_portail_rendu_seconds_count 1");
        assertThat(expose).contains("quantile=\"0.95\"");
    }
}
