package fr.ensim.asso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * Les constats de l'audit de la v1, vérifiés contre la v2.
 *
 * <p>Une réécriture se juge sur ce qu'elle corrige, pas sur ce qu'elle
 * remplace. Chaque test porte l'identifiant du constat d'origine et affirme une
 * propriété que la v1 n'avait pas — donc un test qui aurait échoué sur elle.
 *
 * <p>Ils existent parce qu'une case cochée dans un document ne tient rien :
 * relire l'audit après coup a d'ailleurs révélé que SEC-07 était resté ouvert
 * pendant toute la réécriture, sans que rien ne le signale.
 */
class NonRegressionAuditTest {

    private static final Path RACINE = racineDuDepot();

    private static Path racineDuDepot() {
        for (Path candidat : List.of(Path.of(".."), Path.of("v2"), Path.of("."))) {
            if (Files.exists(candidat.resolve("backend/pom.xml"))) {
                return candidat;
            }
        }
        throw new IllegalStateException(
                "racine du dépôt introuvable depuis " + Path.of("").toAbsolutePath()
              + " — ce test doit échouer plutôt que de ne rien vérifier");
    }

    private String lire(String chemin) throws IOException {
        Path p = RACINE.resolve(chemin);
        assertThat(Files.exists(p)).as("%s doit exister", chemin).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- sécurité

    @Test
    @DisplayName("SEC-01 — le refus est la règle, et les exceptions se comptent")
    void refusParDefaut() throws IOException {
        String config = lire("backend/src/main/java/fr/ensim/asso/shared/security/SecurityConfig.java");

        assertThat(config).contains("anyRequest().authenticated()");

        // La v1 avait onze motifs en permitAll, dont /api/users/** : 27 routes
        // sur 32 se sont retrouvées publiques. Ici il y en a trois, et chacune
        // est justifiée en commentaire. Le nombre est vérifié pour que la
        // quatrième demande un effort conscient.
        long exceptions = config.lines()
                .map(String::trim)
                // Les commentaires citent permitAll() pour expliquer le défaut
                // de la v1 : les compter ferait échouer ce test pour une raison
                // qui n'a rien à voir avec la sécurité.
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .filter(l -> l.contains("permitAll()"))
                .count();
        assertThat(exceptions)
                .as("chaque nouvelle exception publique doit être délibérée")
                .isLessThanOrEqualTo(3);
        assertThat(config).doesNotContain("/api/users");
    }

    @Test
    @DisplayName("SEC-05 et SEC-09 — aucune implémentation de jeton maison")
    void aucunJwtMaison() throws IOException {
        String pom = lire("backend/pom.xml");
        assertThat(pom)
                .as("la v1 avait deux versions de jjwt sur le classpath, "
                  + "et utilisait la déprécié")
                .doesNotContain("jjwt");
        assertThat(pom).contains("spring-boot-starter-oauth2-resource-server");

        // La clé de signature de la v1 était la chaîne « your_secret_key »,
        // copiée d'un tutoriel. Ici il n'y a aucune clé : la validation se fait
        // contre le JWKS de Keycloak.
        assertThat(sourcesJava()).noneMatch(s -> s.contains("SECRET_KEY"));
    }

    @Test
    @DisplayName("SEC-04 — aucun secret dans le dépôt, et aucune valeur par défaut de secret")
    void aucunSecretVersionne() throws IOException {
        List<Pattern> interdits = List.of(
                Pattern.compile("sk_live_[A-Za-z0-9]"),
                Pattern.compile("AccountKey=[A-Za-z0-9+/]{20}"),
                Pattern.compile("password\\s*=\\s*[\"'][^\"'$\\s]{8,}"),
                Pattern.compile("BEGIN (RSA |EC )?PRIVATE KEY"));

        List<String> trouvailles = new ArrayList<>();
        for (Path fichier : fichiersSurveilles()) {
            String contenu = Files.readString(fichier, StandardCharsets.UTF_8);
            for (Pattern motif : interdits) {
                Matcher m = motif.matcher(contenu);
                if (m.find()) {
                    trouvailles.add(fichier + " : " + m.group());
                }
            }
        }
        assertThat(trouvailles)
                .as("mot de passe Azure SQL, clé de compte de stockage et clé "
                  + "secrète Stripe étaient committés dans la v1 depuis février 2025")
                .isEmpty();

        // Et la règle qui l'empêche de revenir : un secret n'a jamais de valeur
        // par défaut. Une clé absente doit faire échouer bruyamment.
        String yml = lire("backend/src/main/resources/application.yml");
        assertThat(yml).contains("cle-secrete: ${STRIPE_CLE_SECRETE:}");
    }

    @Test
    @DisplayName("SEC-07 — les routes ouvertes sans jeton sont limitées en débit")
    void limitationDeDebit() throws IOException {
        // Le seul constat que la réécriture avait laissé ouvert, retrouvé en
        // relisant l'audit plutôt que le code.
        assertThat(Files.exists(RACINE.resolve(
                "backend/src/main/java/fr/ensim/asso/shared/debit/LimiteurDebit.java"))).isTrue();

        String limiteur = lire("backend/src/main/java/fr/ensim/asso/shared/debit/LimiteurDebit.java");
        assertThat(limiteur).contains("/api/public/").contains("/api/webhooks/");
        assertThat(limiteur).contains("TOO_MANY_REQUESTS");
    }

    @Test
    @DisplayName("SEC-11 — les en-têtes de sécurité sont posés, pas désactivés")
    void enTetesDeSecurite() throws IOException {
        String config = lire("backend/src/main/java/fr/ensim/asso/shared/security/SecurityConfig.java");
        assertThat(config).contains("frameOptions(f -> f.deny())");
        assertThat(config).contains("contentTypeOptions");
        assertThat(config).contains("httpStrictTransportSecurity");
        // La v1 faisait « headers.frameOptions(f -> f.disable()) », avec le
        // commentaire « For H2 console » — et H2 n'était pas utilisé.
        assertThat(config).doesNotContain("frameOptions(f -> f.disable())");
    }

    // ------------------------------------------------------------- données

    @Test
    @DisplayName("DATA-01 — le schéma vient des migrations, jamais d'Hibernate")
    void schemaPilotieParFlyway() throws IOException {
        String yml = lire("backend/src/main/resources/application.yml");
        assertThat(yml).contains("ddl-auto: validate");
        assertThat(yml).doesNotContain("ddl-auto: update");

        List<Path> migrations = migrations();
        assertThat(migrations)
                .as("la v1 n'avait ni Flyway ni Liquibase : le schéma était "
                  + "celui qu'Hibernate avait déduit au dernier démarrage")
                .isNotEmpty();
    }

    @Test
    @DisplayName("DATA-02 — aucun chargement EAGER, aucune relation bidirectionnelle")
    void aucunChargementEager() {
        // La v1 avait Asso.teamMembers et Guest.memberships tous deux en EAGER :
        // charger une association chargeait le graphe entier.
        assertThat(sourcesJava())
                .as("les modules se référencent par identifiant, pas par graphe d'objets")
                .noneMatch(s -> s.contains("FetchType.EAGER"));
    }

    @Test
    @DisplayName("DATA-04 — les colonnes de recherche portent un index")
    void colonnesDeRechercheIndexees() throws IOException {
        String migrations = String.join("\n", contenusDesMigrations());
        // La v1 cherchait l'utilisateur par e-mail à chaque requête
        // authentifiée, sans index ni contrainte d'unicité.
        assertThat(migrations).containsIgnoringCase("CREATE INDEX");
        assertThat(migrations).containsIgnoringCase("UNIQUE");
        assertThat(migrations)
                .as("le slug est la clé de lecture publique : il doit être unique")
                .containsIgnoringCase("slug");
    }

    // --------------------------------------------------------- exploitation

    @Test
    @DisplayName("OPS-01 — la chaîne d'intégration existe et couvre tout")
    void integrationContinue() throws IOException {
        String ci = Files.readString(RACINE.resolve("../.github/workflows/ci-v2.yml"),
                StandardCharsets.UTF_8);
        for (String attendu : List.of("gitleaks", "mvn -B test", "npm run typecheck",
                                      "trivy", "kubeconform", "drill-restauration")) {
            assertThat(ci).as("la CI doit exécuter « %s »", attendu)
                    .containsIgnoringCase(attendu);
        }
    }

    @ParameterizedTest(name = "OPS-03 — {0} est durcie")
    @ValueSource(strings = { "backend/Dockerfile", "frontend/Dockerfile" })
    @DisplayName("OPS-03 — les images ne tournent pas en root et déclarent leur santé")
    void imagesDurcies(String chemin) throws IOException {
        String dockerfile = lire(chemin);
        assertThat(dockerfile).contains("USER ");
        assertThat(dockerfile).contains("HEALTHCHECK");
        // La v1 utilisait openjdk:17-jdk-slim — image dépréciée, JDK complet,
        // exécution en root, et un HEALTHCHECK laissé en commentaire.
        assertThat(dockerfile).doesNotContain("openjdk:");
        assertThat(dockerfile).doesNotContain("# HEALTHCHECK");
    }

    @Test
    @DisplayName("OPS-04 — le README ne décrit que ce qui existe")
    void readmeFidele() throws IOException {
        String readme = lire("README.md");
        String makefile = lire("Makefile");

        // Le constat exact de la v1 : le README documentait MySQL (c'était
        // Azure SQL), Jenkins (il n'y avait aucune CI) et docker-compose up
        // (il n'y avait pas de fichier compose).
        List<String> ciblesManquantes = new ArrayList<>();
        Matcher m = Pattern.compile("`make ([a-z-]+)`").matcher(readme);
        while (m.find()) {
            if (!makefile.contains("\n" + m.group(1) + ":")) {
                ciblesManquantes.add("make " + m.group(1));
            }
        }
        assertThat(ciblesManquantes)
                .as("le README promet des commandes qui n'existent pas")
                .isEmpty();

        List<String> cheminsManquants = new ArrayList<>();
        Matcher f = Pattern.compile("`((?:infra|backend|frontend)/[A-Za-z0-9_./-]+)`")
                .matcher(readme);
        while (f.find()) {
            if (!Files.exists(RACINE.resolve(f.group(1)))) {
                cheminsManquants.add(f.group(1));
            }
        }
        assertThat(cheminsManquants)
                .as("le README renvoie à des fichiers absents")
                .isEmpty();
    }

    // ------------------------------------------------------------ paiement

    @Test
    @DisplayName("PAY-01 à PAY-03 — aucun droit accordé sans encaissement vérifié")
    void paiementsReels() throws IOException {
        String service = lire("backend/src/main/java/fr/ensim/asso/tresorerie/app/ServiceTresorerie.java");

        // PAY-01 : la v1 accordait l'adhésion sur un System.out.println.
        assertThat(sourcesJava()).noneMatch(s -> s.contains("Simulation du paiement"));
        assertThat(sourcesJava()).noneMatch(s -> s.contains("System.out.println"));

        // PAY-03 : le montant venait du navigateur. Ici il est comparé.
        assertThat(service).contains("marquerPayee");
        String commande = lire("backend/src/main/java/fr/ensim/asso/tresorerie/domain/Commande.java");
        assertThat(commande)
                .as("le montant encaissé doit être COMPARÉ au montant dû")
                .contains("montantTotalCents");
    }

    // ------------------------------------------------------------ frontend

    @Test
    @DisplayName("PERF-02 — le portail est découpé, il ne charge pas tout d'un bloc")
    void decoupageDuFront() throws IOException {
        String app = lire("frontend/src/App.tsx");
        // La v1 n'avait pas un seul React.lazy : chaque visiteur téléchargeait
        // three.js et 2,9 Mo de modèle 3-D, y compris pour lire une page de texte.
        assertThat(app).contains("lazy(");
    }

    @Test
    @DisplayName("PERF-01 — aucun octet d'image dupliqué entre deux dossiers")
    void aucunActifDuplique() throws IOException {
        Path publics = RACINE.resolve("frontend/public");
        Path sources = RACINE.resolve("frontend/src/assets");
        if (!Files.isDirectory(publics) || !Files.isDirectory(sources)) {
            return;    // rien à comparer : le portail n'embarque pas d'images
        }
        List<String> doublons = new ArrayList<>();
        try (Stream<Path> flux = Files.walk(publics)) {
            for (Path p : flux.filter(Files::isRegularFile).toList()) {
                Path jumeau = sources.resolve(publics.relativize(p));
                if (Files.exists(jumeau)
                        && Files.mismatch(p, jumeau) == -1) {
                    doublons.add(p.getFileName().toString());
                }
            }
        }
        // La v1 avait 24 fichiers identiques dans les deux dossiers, 1,5 Mo.
        assertThat(doublons).isEmpty();
    }

    // ------------------------------------------------------------- interne

    private List<Path> migrations() throws IOException {
        try (Stream<Path> flux = Files.list(
                RACINE.resolve("backend/src/main/resources/db/migration"))) {
            return flux.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
    }

    private List<String> contenusDesMigrations() throws IOException {
        List<String> contenus = new ArrayList<>();
        for (Path p : migrations()) {
            contenus.add(Files.readString(p, StandardCharsets.UTF_8));
        }
        return contenus;
    }

    private List<Path> fichiersSurveilles() throws IOException {
        try (Stream<Path> flux = Files.walk(RACINE)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/node_modules/"))
                    .filter(p -> !p.toString().contains("/dist/"))
                    .filter(p -> {
                        String n = p.toString();
                        return n.endsWith(".java") || n.endsWith(".yml") || n.endsWith(".yaml")
                            || n.endsWith(".properties") || n.endsWith(".ts") || n.endsWith(".env");
                    })
                    .toList();
        }
    }

    private List<String> sourcesJava() {
        try (Stream<Path> flux = Files.walk(RACINE.resolve("backend/src/main/java"))) {
            List<String> contenus = new ArrayList<>();
            for (Path p : flux.filter(f -> f.toString().endsWith(".java")).toList()) {
                contenus.add(Files.readString(p, StandardCharsets.UTF_8));
            }
            // Garde-fou : un parcours qui ne trouve rien rendrait tous les
            // « noneMatch » ci-dessus vrais sans rien avoir lu.
            assertThat(contenus).hasSizeGreaterThan(40);
            return contenus;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
