package fr.ensim.asso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'infrastructure démarrée doit correspondre à celle qui est utilisée.
 *
 * <p>Une dérive constatée : {@code make up} démarrait Redpanda, Meilisearch et
 * Mailpit alors qu'aucune ligne de code ne les référençait. Trois conteneurs
 * sur la machine de chaque contributeur, et surtout l'impression que le système
 * publie des évènements, indexe une recherche et envoie des courriels — dont il
 * ne fait rien.
 *
 * <p>C'est la même faute qu'OPS-04, à l'envers : au lieu de documenter une
 * infrastructure absente, on démarrait une infrastructure inutilisée. Les deux
 * font croire à des capacités qui n'existent pas.
 *
 * <p>La vérification tient dans les deux sens, et c'est ce qui la rend utile :
 * un service démarré par défaut doit être référencé par la configuration, et un
 * service rangé dans le profil « futur » ne doit PAS l'être. Brancher Redpanda
 * sans le sortir du profil fait échouer ce test.
 */
class InfrastructureUtiliseeTest {

    private static final Pattern SERVICE =
            Pattern.compile("^  ([a-z][a-z0-9-]*):$", Pattern.MULTILINE);

    private Path racine() {
        for (Path candidat : List.of(Path.of(".."), Path.of("v2"), Path.of("."))) {
            if (Files.exists(candidat.resolve("docker-compose.dev.yml"))) {
                return candidat;
            }
        }
        throw new IllegalStateException("docker-compose.dev.yml introuvable depuis "
                + Path.of("").toAbsolutePath());
    }

    /** Nom du service → ports publiés, et s'il est dans un profil. */
    private Map<String, Boolean> servicesEtProfils(String compose) {
        Map<String, Boolean> resultat = new LinkedHashMap<>();
        Matcher m = SERVICE.matcher(compose);
        List<int[]> bornes = new ArrayList<>();
        List<String> noms = new ArrayList<>();
        while (m.find()) {
            bornes.add(new int[] { m.start(), m.end() });
            noms.add(m.group(1));
        }
        for (int i = 0; i < noms.size(); i++) {
            int debut = bornes.get(i)[1];
            int fin = i + 1 < noms.size() ? bornes.get(i + 1)[0] : compose.length();
            resultat.put(noms.get(i), compose.substring(debut, fin).contains("profiles:"));
        }
        return resultat;
    }

    private List<String> ports(String compose, String service) {
        Matcher m = SERVICE.matcher(compose);
        int debut = -1, fin = compose.length();
        while (m.find()) {
            if (debut >= 0) { fin = m.start(); break; }
            if (m.group(1).equals(service)) { debut = m.end(); }
        }
        String bloc = compose.substring(debut, fin);
        List<String> publies = new ArrayList<>();
        Matcher p = Pattern.compile("[\"']?(\\d{4,5}):\\d{4,5}[\"']?").matcher(bloc);
        while (p.find()) {
            publies.add(p.group(1));
        }
        return publies;
    }

    @Test
    @DisplayName("tout service démarré par défaut est réellement utilisé, et réciproquement")
    void infrastructureEnAccordAvecLeCode() throws IOException {
        Path racine = racine();
        String compose = Files.readString(racine.resolve("docker-compose.dev.yml"), StandardCharsets.UTF_8);
        // Le Makefile compte comme configuration — il porte l'URL de la base
        // pour le chargement du jeu de données — mais PAS ses lignes « @echo » :
        // afficher une URL à l'écran n'est pas s'en servir. Sans ce filtre, un
        // service parfaitement inutilisé passerait pour branché parce qu'on en
        // imprime l'adresse.
        String makefile = Files.readString(racine.resolve("Makefile"), StandardCharsets.UTF_8)
                .lines()
                .filter(l -> !l.strip().startsWith("@echo"))
                .reduce("", (a, b) -> a + "\n" + b);
        String config = Files.readString(
                racine.resolve("backend/src/main/resources/application.yml"), StandardCharsets.UTF_8)
            + makefile;

        Map<String, Boolean> services = servicesEtProfils(compose);
        assertThat(services)
                .as("l'analyse du fichier compose n'a trouvé aucun service : "
                  + "son format a changé et ce test ne vérifierait plus rien")
                .hasSizeGreaterThan(4);

        List<String> inutilises = new ArrayList<>();
        List<String> branchesMaisCaches = new ArrayList<>();

        for (var entree : services.entrySet()) {
            String service = entree.getKey();
            boolean dansUnProfil = entree.getValue();
            List<String> portsPublies = ports(compose, service);
            if (portsPublies.isEmpty()) {
                continue;
            }
            boolean reference = portsPublies.stream().anyMatch(config::contains);

            if (!dansUnProfil && !reference) {
                inutilises.add(service + " (ports " + portsPublies + ")");
            }
            if (dansUnProfil && reference) {
                branchesMaisCaches.add(service);
            }
        }

        assertThat(inutilises)
                .as("ces services sont démarrés par « make up » sans qu'aucune "
                  + "configuration ne les utilise : les ranger dans le profil « futur »")
                .isEmpty();
        assertThat(branchesMaisCaches)
                .as("ces services sont utilisés par la configuration mais rangés "
                  + "dans un profil : « make up » ne les démarrera pas, et "
                  + "l'application échouera au démarrage")
                .isEmpty();
    }

    @Test
    @DisplayName("« make down » arrête aussi ce que « make up-tout » a démarré")
    void arretComplet() throws IOException {
        String makefile = Files.readString(racine().resolve("Makefile"), StandardCharsets.UTF_8);
        // Un « down » qui ignore les profils laisse tourner des conteneurs que
        // l'on croit arrêtés — et qui tiennent les ports au prochain « up ».
        assertThat(makefile).contains("--profile futur down");
    }
}
