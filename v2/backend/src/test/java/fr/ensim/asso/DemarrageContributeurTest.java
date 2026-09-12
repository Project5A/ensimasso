package fr.ensim.asso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-02 : « un autre ingénieur ne peut pas faire tourner ce projet ».
 *
 * <p>Le constat le plus embarrassant à reproduire, et il l'a été : le Makefile
 * appelait {@code ./backend/mvnw}, absent du dépôt. Toutes les cibles Maven —
 * {@code test}, {@code run}, {@code build}, {@code migrate} — échouaient dès le
 * premier clone. La CI ne l'a pas vu parce qu'elle appelle {@code mvn}
 * directement après {@code setup-java} : chaîne verte, contributeur bloqué.
 *
 * <p>Ces tests vérifient ce qu'un nouveau venu rencontre, dans l'ordre où il le
 * rencontre.
 */
class DemarrageContributeurTest {

    private Path racine() {
        for (Path candidat : List.of(Path.of(".."), Path.of("v2"), Path.of("."))) {
            if (Files.exists(candidat.resolve("Makefile"))) {
                return candidat;
            }
        }
        throw new IllegalStateException("Makefile introuvable depuis "
                + Path.of("").toAbsolutePath());
    }

    private String makefile() throws IOException {
        return Files.readString(racine().resolve("Makefile"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("tout exécutable appelé par le Makefile existe dans le dépôt")
    void executablesPresents() throws IOException {
        String makefile = makefile();
        Path racine = racine();

        List<String> introuvables = new ArrayList<>();
        // Les appels de la forme ./chemin/outil, y compris à travers une
        // variable comme $(MVN) := ./backend/mvnw.
        Matcher m = Pattern.compile("(\\./[A-Za-z0-9_./-]+)").matcher(makefile);
        while (m.find()) {
            String chemin = m.group(1);
            if (chemin.endsWith("/")) {
                continue;
            }
            if (!Files.exists(racine.resolve(chemin.substring(2)))) {
                introuvables.add(chemin);
            }
        }

        assertThat(introuvables)
                .as("le Makefile appelle des exécutables absents : c'est OPS-02, "
                  + "et la CI ne le verra pas parce qu'elle n'utilise pas le Makefile")
                .isEmpty();
    }

    @Test
    @DisplayName("l'enveloppe Maven est versionnée et exécutable")
    void envelopeMaven() throws IOException {
        Path racine = racine();
        Path mvnw = racine.resolve("backend/mvnw");

        assertThat(Files.exists(mvnw))
                .as("sans enveloppe, faire tourner le projet exige d'installer "
                  + "la bonne version de Maven à la main")
                .isTrue();
        assertThat(Files.isExecutable(mvnw))
                .as("un mvnw non exécutable échoue avec « Permission denied », "
                  + "ce qui n'aide personne")
                .isTrue();
        assertThat(Files.exists(racine.resolve("backend/.mvn/wrapper/maven-wrapper.properties")))
                .isTrue();
    }

    @Test
    @DisplayName("le modèle .env déclare tout ce dont le compose a besoin, et rien de mort")
    void modeleEnvComplet() throws IOException {
        Path racine = racine();
        String compose = Files.readString(racine.resolve("docker-compose.dev.yml"),
                StandardCharsets.UTF_8);
        String exemple = Files.readString(racine.resolve(".env.example"), StandardCharsets.UTF_8);

        List<String> requises = new ArrayList<>();
        Matcher m = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)").matcher(compose);
        while (m.find()) {
            requises.add(m.group(1));
        }
        assertThat(requises).isNotEmpty();

        List<String> manquantes = requises.stream()
                .filter(v -> !exemple.contains(v + "="))
                .distinct().toList();
        assertThat(manquantes)
                .as("une variable absente du modèle donne une erreur au démarrage "
                  + "que le nouveau venu ne saura pas relier à sa cause")
                .isEmpty();

        // Et l'inverse : une variable que plus rien ne lit se retrouve
        // renseignée avec soin par quelqu'un qui croit qu'elle sert.
        String toutLeProjet = concatener(racine);
        List<String> mortes = new ArrayList<>();
        Matcher d = Pattern.compile("^([A-Z_][A-Z0-9_]*)=", Pattern.MULTILINE).matcher(exemple);
        while (d.find()) {
            if (!toutLeProjet.contains(d.group(1))) {
                mortes.add(d.group(1));
            }
        }
        assertThat(mortes)
                .as("ces variables ne sont lues nulle part : les retirer du modèle")
                .isEmpty();
    }

    @Test
    @DisplayName("les cibles annoncées « sans Docker » n'invoquent pas docker")
    void ciblesSansDocker() throws IOException {
        String makefile = makefile();
        // « make test » est annoncé « aucun Docker requis » : c'est la porte
        // d'entrée de quelqu'un qui veut juste vérifier que le projet compile.
        int debut = makefile.indexOf("\ntest:");
        assertThat(debut).isPositive();
        int fin = makefile.indexOf("\n.PHONY", debut);
        String cible = makefile.substring(debut, fin > 0 ? fin : makefile.length());
        assertThat(cible).doesNotContain("docker");
        assertThat(cible).doesNotContain("$(COMPOSE)");
    }

    @Test
    @DisplayName("toute cible qui lance npm installe d'abord les dépendances")
    void ciblesNpmInstallentLeursDependances() throws IOException {
        // Depuis un clone neuf, node_modules n'existe pas : « npm run
        // typecheck » échoue alors sur « Cannot find module 'react' », et
        // envoie chercher un problème de typage là où il n'y a qu'un dossier
        // absent. Constaté sur « make front-test ».
        List<String> fautives = new ArrayList<>();
        for (String recetteBrute : makefile().split("\n\n")) {
            // Sans retirer les commentaires, un « # npm ci d'abord » suffit à
            // satisfaire la vérification. C'est la troisième fois dans ce dépôt
            // qu'un contrôle se valide sur son propre commentaire : compter les
            // permitAll(), les @echo du Makefile, et maintenant ceci.
            String recette = recetteBrute.lines()
                    .filter(l -> !l.strip().startsWith("#"))
                    .reduce("", (a, b) -> a + "\n" + b);
            if (!recette.contains("npm run") && !recette.contains("npm test")) {
                continue;
            }
            if (!recette.contains("npm ci") && !recette.contains("npm install")) {
                // Nommer la cible, pas le « .PHONY » qui la précède : un test
                // qui échoue doit dire quoi corriger.
                String nom = recette.lines()
                        .filter(l -> l.contains(":") && !l.startsWith("\t"))
                        .filter(l -> !l.startsWith(".PHONY"))
                        .findFirst().orElse(recette).split(":")[0];
                fautives.add(nom.trim());
            }
        }
        assertThat(fautives)
                .as("ces cibles lancent npm sans garantir que les dépendances "
                  + "sont installées")
                .isEmpty();
    }

    private String concatener(Path racine) throws IOException {
        StringBuilder tout = new StringBuilder();
        try (Stream<Path> flux = Files.walk(racine)) {
            for (Path p : flux.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().contains("/target/"))
                    .filter(f -> !f.toString().contains("/node_modules/"))
                    .filter(f -> {
                        String n = f.toString();
                        return n.endsWith(".java") || n.endsWith(".yml") || n.endsWith(".yaml")
                            || n.endsWith(".sh") || n.endsWith("Makefile") || n.endsWith(".ts")
                            || n.endsWith("Dockerfile") || n.endsWith(".md");
                    }).toList()) {
                tout.append(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        assertThat(tout.length()).isGreaterThan(50_000);
        return tout.toString();
    }
}
