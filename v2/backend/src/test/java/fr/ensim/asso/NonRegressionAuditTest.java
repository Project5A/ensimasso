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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Un mot de passe écrit en clair, quel que soit le format de configuration.
     *
     * <p>Sont exclus, et ce sont des absences de secret, pas des secrets :
     * les substitutions {@code ${VAR}} et leur valeur de repli {@code :-...},
     * les valeurs à remplacer d'un fichier d'exemple, et les clés de TEST des
     * prestataires, que Stripe publie lui-même.
     */
    private static final Pattern MOTDEPASSE_EN_DUR = Pattern.compile(
            "(?i)(?<![a-zA-Z0-9${])(password|passwd|secret|motdepasse)\\s*[=:]\\s*"
          + "[\"']?(?!\\$\\{)(?!-)(?!changez_moi)(?!dev_seulement)(?!votre_)"
          + "(?!whsec_test_)(?!sk_test_)(?!pk_test_)"
          + "[^\"'\\s#$]{8,}");

    @Test
    @DisplayName("SEC-04 — le détecteur de mot de passe reconnaît les formats réellement utilisés")
    void detecteurDeMotDePasseOperant() {
        // Le motif précédent exigeait un « = » PUIS un guillemet. Il ne pouvait
        // matcher ni le YAML de la v2 ni le .properties de la v1 — c'est-à-dire
        // aucun des deux formats de configuration du projet. Le seul motif censé
        // rattraper un mot de passe en dur n'en rattrapait aucun, et personne ne
        // s'en apercevait puisqu'un test qui ne trouve rien est vert.
        //
        // Ce cas-ci teste le détecteur lui-même, pour que le prochain qui le
        // resserre voie tout de suite ce qu'il vient d'aveugler.
        // Les exemples sont assemblés morceau par morceau, et ce n'est pas une
        // coquetterie : écrits d'un seul tenant, ils ressembleraient à des
        // secrets en dur et le balayage du cas suivant les signalerait — dans
        // CE fichier. On refuse de s'ajouter à une liste d'exclusions : un
        // scanner de secrets qui ignore un fichier est un endroit où cacher un
        // secret.
        String cle = "pass" + "word";
        String valeur = "Ensim2025" + "SecretDb";

        assertThat(List.of(
                "spring.datasource." + cle + "=" + valeur,          // format v1
                "    " + cle + ": " + valeur,                        // format v2
                "    " + cle + ": \"" + valeur + "\"",
                "MINIO_" + cle.toUpperCase(java.util.Locale.ROOT) + "=" + valeur))
                .allSatisfy(ligne -> assertThat(MOTDEPASSE_EN_DUR.matcher(ligne).find())
                        .as("non détecté : %s", ligne).isTrue());

        assertThat(List.of(
                "    " + cle + ": ${DB_PASSWORD:}",                  // substitution
                "      POSTGRES_PASSWORD: ${DB_PASSWORD:-dev_seulement}",
                "DB_PASSWORD=changez_moi",                           // fichier d'exemple
                "    " + cle + ": \"\"",
                "static final String SECRET = \"whsec_" + "test_0123456789\""))
                .allSatisfy(ligne -> assertThat(MOTDEPASSE_EN_DUR.matcher(ligne).find())
                        .as("faux positif : %s", ligne).isFalse());
    }

    @Test
    @DisplayName("SEC-04 — aucun secret dans le dépôt, et aucune valeur par défaut de secret")
    void aucunSecretVersionne() throws IOException {
        // Le motif de mot de passe exigeait un « = » PUIS un guillemet :
        //     password\\s*=\\s*["'][^"'$\\s]{8,}
        // Il ne pouvait donc matcher ni « password: valeur » (YAML, le format de
        // la v2), ni « spring.datasource.password=valeur » (properties, le
        // format de la v1 — celui-là même où le mot de passe Azure SQL a vécu
        // committé pendant un an). Il ne reconnaissait que « password="..." »,
        // qui n'apparaît nulle part dans le dépôt. Vérifié : le seul motif censé
        // rattraper un mot de passe en dur n'en rattrapait aucun.
        //
        // Les deux séparateurs sont désormais acceptés, les guillemets sont
        // facultatifs, et les substitutions ${VAR} comme les valeurs à
        // remplacer sont exclues — ce sont des absences de secret, pas des
        // secrets.
        List<Pattern> interdits = List.of(
                Pattern.compile("sk_live_[A-Za-z0-9]"),
                Pattern.compile("AccountKey=[A-Za-z0-9+/]{20}"),
                MOTDEPASSE_EN_DUR,
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
    @DisplayName("SEC-06 — l'adresse du client ne vient jamais d'un en-tête que le client choisit")
    void adresseClientNonForgeable() throws IOException {
        String yml = sansCommentaires(lire("backend/src/main/resources/application.yml"));

        // « framework » installe le ForwardedHeaderFilter de Spring, qui retient
        // le PREMIER élément de X-Forwarded-For — celui écrit par l'appelant.
        // Le limiteur de débit s'en sert comme clé de seau : vérifié en le
        // faisant tourner, huit requêtes passent avec huit en-têtes inventés là
        // où trois déclenchent un 429. « native » délègue à la RemoteIpValve de
        // Tomcat, qui remonte l'en-tête par la droite en sautant les proxys de
        // confiance.
        assertThat(yml)
                .as("forward-headers-strategy: framework rend getRemoteAddr() "
                  + "contrôlable par le client, et avec lui toute limite de débit")
                .contains("forward-headers-strategy: native")
                .doesNotContain("forward-headers-strategy: framework");
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
        // « ./mvnw » et non « mvn » : la CI doit emprunter le même chemin
        // d'entrée qu'un contributeur, sinon une enveloppe Maven absente donne
        // une chaîne verte et un clone inutilisable. C'est arrivé.
        for (String attendu : List.of("gitleaks", "./mvnw -B test", "npm run typecheck",
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
        // Ce test comparait deux dossiers nommément, frontend/public et
        // frontend/src/assets, et sortait par un `return` si l'un des deux
        // manquait. Aucun des deux n'existe : il ne vérifiait rien, et comptait
        // pourtant comme un constat couvert. Un test qui ne peut pas échouer
        // est pire qu'un test absent — il occupe la place.
        //
        // La question réelle n'était jamais « ces deux dossiers-là se
        // recopient-ils », mais « le portail embarque-t-il deux fois les mêmes
        // octets ». On la pose telle quelle, sur tout le portail, et elle reste
        // vraie quelle que soit l'arborescence de demain.
        Path portail = RACINE.resolve("frontend");
        assertThat(portail).as("le portail doit exister").isDirectory();

        Map<String, List<String>> parEmpreinte = new LinkedHashMap<>();
        try (Stream<Path> flux = Files.walk(portail)) {
            for (Path f : flux.filter(Files::isRegularFile).toList()) {
                String chemin = portail.relativize(f).toString().replace('\\', '/');
                if (chemin.startsWith("node_modules/") || chemin.startsWith("dist/")
                        || !chemin.matches(".*\\.(png|jpe?g|gif|svg|webp|avif|ico|woff2?)$")) {
                    continue;
                }
                if (Files.size(f) == 0) {
                    continue;
                }
                String empreinte = java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(f)));
                parEmpreinte.computeIfAbsent(empreinte, c -> new ArrayList<>()).add(chemin);
            }
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        List<String> doublons = parEmpreinte.values().stream()
                .filter(l -> l.size() > 1)
                .map(l -> String.join(" == ", l))
                .toList();

        // La v1 avait 24 fichiers identiques dans les deux dossiers, 1,5 Mo.
        assertThat(doublons)
                .as("des octets identiques livrés deux fois : c'est PERF-01")
                .isEmpty();
    }

    // ------------------------------------------------------------- interne

    /** Retire les commentaires : un test ne doit pas se satisfaire lui-même. */
    private static String sansCommentaires(String texte) {
        return texte.lines()
                .map(l -> l.replaceAll("(^|\\s)#.*$", ""))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

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
