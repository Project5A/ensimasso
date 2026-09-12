package fr.ensim.asso;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

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
 */
class MetriquesTest {

    private final PrometheusMeterRegistry registre =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

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
