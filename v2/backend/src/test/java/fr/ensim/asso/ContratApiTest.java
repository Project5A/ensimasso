package fr.ensim.asso;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le front ne doit pas pouvoir appeler une route qui n'existe pas.
 *
 * <p>C'est un défaut constaté dans la v1, pas une précaution théorique : le
 * tableau de bord appelait {@code PUT /api/events/{id}},
 * {@code DELETE /api/events/{id}} et {@code DELETE /api/posts/{id}}, dont aucun
 * n'était implémenté. Le bouton existait, le clic produisait un 405, et rien
 * dans la chaîne de construction ne le signalait.
 *
 * <p>Ce test lit le client d'API du front et le confronte aux routes
 * réellement déclarées par les contrôleurs. Il ne remplace pas un contrat
 * OpenAPI typé — il en fait le travail le plus utile, pour le prix d'un test.
 */
class ContratApiTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("fr.ensim.asso");

    /** Une route, réduite à ce qui doit correspondre : verbe et gabarit de chemin. */
    private record Route(String methode, String chemin) {
        @Override public String toString() { return methode + " " + chemin; }
    }

    @Test
    @DisplayName("toute route appelée par le front existe côté serveur")
    void aucuneRouteFantome() throws IOException {
        Set<Route> serveur = routesDuServeur();
        List<Route> front = routesDuFront();

        // Garde-fou contre le pire résultat possible : un test qui passe parce
        // qu'il n'a rien analysé. La leçon a été apprise ailleurs dans ce dépôt,
        // avec une empreinte de base de données qui ne comparait rien.
        assertThat(front)
                .as("l'analyse du client d'API n'a trouvé aucune route : le format du "
                  + "fichier a dû changer, et ce test ne vérifierait plus rien")
                .hasSizeGreaterThan(15);
        assertThat(serveur).hasSizeGreaterThan(15);

        List<Route> fantomes = front.stream().filter(r -> !serveur.contains(r)).toList();

        assertThat(fantomes)
                .as("routes appelées par le front et absentes du serveur.%n"
                  + "Routes déclarées côté serveur :%n  %s",
                    String.join("%n  ".formatted(), serveur.stream().map(Object::toString).toList()))
                .isEmpty();
    }

    @Test
    @DisplayName("le contrat couvre bien les deux modules ajoutés récemment")
    void couvertureDesNouveauxModules() throws IOException {
        // Sans cette assertion, la précédente resterait verte si l'analyse du
        // front cessait de voir les appels les plus récents.
        List<String> appels = routesDuFront().stream().map(Object::toString).toList();
        assertThat(appels).anyMatch(r -> r.contains("/api/agenda/"));
        assertThat(appels).anyMatch(r -> r.contains("/api/partenaires/"));
        assertThat(appels).anyMatch(r -> r.startsWith("DELETE "));
    }

    /**
     * Les modules que le serveur expose et qu'AUCUN écran n'appelle.
     *
     * <p>Le test précédent ne regarde que dans un sens : « le front appelle-t-il
     * une route fantôme ? ». L'autre sens ne se voyait nulle part, et il est
     * plus lourd de conséquences ici : deux modules entiers — adhésions et
     * trésorerie — sont écrits, autorisés, testés, et atteignables par
     * personne d'autre que curl. Quatorze routes.
     *
     * <p>Ce n'est pas un bogue à corriger dans ce test, c'est un état du projet
     * qu'il faut garder DIT. Le README coche « Module adhesion » et « Module
     * tresorerie » à côté de « Tableau de bord » et « Portail public », ce qui
     * met sur la même ligne un module qui existe et une fonctionnalité qu'un
     * humain peut atteindre.
     *
     * <p>La liste rétrécit : `/api/medias` puis `/api/passations` en sont
     * sortis le jour où leurs écrans ont été écrits, et c'est ce test qui l'a
     * signalé les deux fois.
     *
     * <p>La liste est donc figée ici ET dans le README. Le jour où un écran de
     * médiathèque est écrit, ce test devient rouge — et c'est ce qu'on veut :
     * il force à mettre le README à jour au lieu de le laisser vieillir.
     */
    private static final Set<String> MODULES_SANS_ECRAN =
            Set.of("/api/adhesions", "/api/tresorerie");

    @Test
    @DisplayName("les modules qu'aucun écran n'atteint sont exactement ceux que le README annonce")
    void modulesSansEcran() throws IOException {
        Set<String> prefixesServeur = new TreeSet<>();
        for (Route r : routesDuServeur()) {
            Matcher m = Pattern.compile("^(/api/[a-z-]+)").matcher(r.chemin());
            if (m.find()) {
                prefixesServeur.add(m.group(1));
            }
        }
        // Appelé par Stripe, jamais par un navigateur : son absence côté front
        // est la conception, pas un manque.
        prefixesServeur.remove("/api/webhooks");

        assertThat(prefixesServeur)
                .as("l'analyse n'a trouvé presque aucun module : elle ne vérifie plus rien")
                .hasSizeGreaterThan(5);

        String clientApi = Files.readString(
                clientDuFront(), StandardCharsets.UTF_8);
        Set<String> sansEcran = new TreeSet<>();
        for (String prefixe : prefixesServeur) {
            if (!clientApi.contains(prefixe)) {
                sansEcran.add(prefixe);
            }
        }

        assertThat(sansEcran)
                .as("la liste des modules sans écran a changé : mettez à jour "
                  + "MODULES_SANS_ECRAN et la section correspondante du README")
                .isEqualTo(new TreeSet<>(MODULES_SANS_ECRAN));

        // Et le README doit les nommer. Un état connu qui n'est écrit nulle part
        // est un état oublié.
        String readme = Files.readString(readmeV2(), StandardCharsets.UTF_8);
        for (String prefixe : MODULES_SANS_ECRAN) {
            assertThat(readme)
                    .as("le README doit nommer %s parmi les modules sans écran", prefixe)
                    .contains(prefixe);
        }
    }

    @Test
    @DisplayName("les types de média acceptés par le champ de dépôt sont ceux du serveur")
    void typesDeMediaAccordes() throws IOException {
        // Le champ `<input type="file" accept=…>` de la médiathèque est la
        // première barrière : il décide de ce que l'utilisateur peut même
        // CHOISIR. S'il est plus large que le serveur, on laisse quelqu'un
        // téléverser quinze méga-octets pour se faire répondre non au bout ;
        // s'il est plus étroit, on lui cache un type que le serveur accepte.
        // Le test côté front ne peut pas trancher cela : il n'a accès qu'à sa
        // propre constante. Le croisement se fait ici.
        String service = Files.readString(
                cheminDu("backend/src/main/java/fr/ensim/asso/media/app/ServiceMedia.java"),
                StandardCharsets.UTF_8);
        String clientApi = Files.readString(clientDuFront(), StandardCharsets.UTF_8);

        Set<String> duServeur = new TreeSet<>();
        Matcher ms = Pattern.compile("\"((?:image|application)/[a-z0-9.+-]+)\",\\s*\"[a-z0-9]+\"")
                .matcher(service);
        while (ms.find()) {
            duServeur.add(ms.group(1));
        }

        Set<String> duFront = new TreeSet<>();
        Matcher bloc = Pattern.compile(
                "TYPES_MEDIA_ACCEPTES\\s*=\\s*\\[(.*?)]", Pattern.DOTALL).matcher(clientApi);
        if (bloc.find()) {
            Matcher mf = Pattern.compile("'((?:image|application)/[a-z0-9.+-]+)'").matcher(bloc.group(1));
            while (mf.find()) {
                duFront.add(mf.group(1));
            }
        }

        // Garde-fou : un test qui n'a rien extrait passerait en comparant deux
        // ensembles vides.
        assertThat(duServeur)
                .as("aucun type extrait de ServiceMedia : le format a changé, "
                  + "ce test ne vérifierait plus rien")
                .hasSizeGreaterThan(3);
        assertThat(duFront)
                .as("aucun type extrait de api.ts : le format a changé")
                .hasSizeGreaterThan(3);

        assertThat(duFront)
                .as("le champ de dépôt et le serveur doivent accepter les mêmes types")
                .isEqualTo(duServeur);
    }

    // ------------------------------------------------------------- serveur

    private Set<Route> routesDuServeur() {
        Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::toString));
        for (JavaClass jc : CLASSES) {
            Class<?> classe;
            try {
                classe = Class.forName(jc.getFullName(), false, getClass().getClassLoader());
            } catch (Throwable e) {
                continue;
            }
            if (classe.getAnnotation(RestController.class) == null) {
                continue;
            }
            RequestMapping base = classe.getAnnotation(RequestMapping.class);
            String prefixe = base != null && base.value().length > 0 ? base.value()[0] : "";

            for (Method m : classe.getDeclaredMethods()) {
                for (Route r : routesDe(m)) {
                    routes.add(new Route(r.methode(), gabarit(prefixe + r.chemin())));
                }
            }
        }
        return routes;
    }

    private List<Route> routesDe(Method m) {
        List<Route> routes = new ArrayList<>();
        ajouter(routes, m.getAnnotation(GetMapping.class), "GET");
        ajouter(routes, m.getAnnotation(PostMapping.class), "POST");
        ajouter(routes, m.getAnnotation(PutMapping.class), "PUT");
        ajouter(routes, m.getAnnotation(DeleteMapping.class), "DELETE");
        ajouter(routes, m.getAnnotation(PatchMapping.class), "PATCH");

        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            String[] chemins = rm.value().length > 0 ? rm.value() : new String[] { "" };
            for (RequestMethod verbe : rm.method().length > 0 ? rm.method() : RequestMethod.values()) {
                for (String c : chemins) {
                    routes.add(new Route(verbe.name(), c));
                }
            }
        }
        return routes;
    }

    private void ajouter(List<Route> routes, Annotation a, String verbe) {
        if (a == null) return;
        String[] chemins = switch (verbe) {
            case "GET" -> ((GetMapping) a).value();
            case "POST" -> ((PostMapping) a).value();
            case "PUT" -> ((PutMapping) a).value();
            case "DELETE" -> ((DeleteMapping) a).value();
            default -> ((PatchMapping) a).value();
        };
        if (chemins.length == 0) {
            routes.add(new Route(verbe, ""));
            return;
        }
        for (String c : chemins) {
            routes.add(new Route(verbe, c));
        }
    }

    // --------------------------------------------------------------- front

    /** Un littéral de chemin d'API dans le client du front. */
    private static final Pattern CHEMIN =
            Pattern.compile("[`'\"](/api/[^`'\"]*)[`'\"]");
    /** Le verbe, quand il est précisé dans les options de la requête. */
    private static final Pattern VERBE =
            Pattern.compile("method:\\s*'([A-Z]+)'");

    private List<Route> routesDuFront() throws IOException {
        String source = Files.readString(clientDuFront(), StandardCharsets.UTF_8);
        List<Route> routes = new ArrayList<>();

        Matcher m = CHEMIN.matcher(source);
        List<int[]> positions = new ArrayList<>();
        List<String> chemins = new ArrayList<>();
        while (m.find()) {
            positions.add(new int[] { m.start(), m.end() });
            chemins.add(m.group(1));
        }

        for (int i = 0; i < chemins.size(); i++) {
            // Le verbe éventuel se trouve entre ce chemin et le suivant : au-delà,
            // il appartient déjà à un autre appel.
            int debut = positions.get(i)[1];
            int fin = i + 1 < positions.size() ? positions.get(i + 1)[0] : source.length();
            Matcher v = VERBE.matcher(source.substring(debut, fin));
            String verbe = v.find() ? v.group(1) : "GET";
            routes.add(new Route(verbe, gabarit(chemins.get(i))));
        }
        return routes;
    }

    /** Un chemin du dépôt, que Surefire tourne depuis backend/ ou depuis la racine. */
    private Path cheminDu(String relatifDepuisV2) {
        for (Path candidat : List.of(
                Path.of("..").resolve(relatifDepuisV2),
                Path.of("v2").resolve(relatifDepuisV2),
                Path.of(relatifDepuisV2.replaceFirst("^backend/", "")))) {
            if (Files.exists(candidat)) {
                return candidat;
            }
        }
        throw new IllegalStateException(
                relatifDepuisV2 + " introuvable depuis " + Path.of("").toAbsolutePath()
              + " — ce test doit échouer plutôt que de ne rien vérifier");
    }

    private Path readmeV2() {
        for (Path candidat : List.of(
                Path.of("../README.md"),
                Path.of("v2/README.md"),
                Path.of("README.md"))) {
            // Celui de la v2, reconnu à son contenu : la racine du dépôt en
            // porte un autre, et lire le mauvais rendrait ce test muet.
            if (Files.exists(candidat)) {
                try {
                    if (Files.readString(candidat, StandardCharsets.UTF_8).contains("Reste à faire")) {
                        return candidat;
                    }
                } catch (IOException e) {
                    // fichier illisible : on continue de chercher
                }
            }
        }
        throw new IllegalStateException(
                "README de la v2 introuvable depuis " + Path.of("").toAbsolutePath()
              + " — ce test doit échouer plutôt que de ne rien vérifier");
    }

    private Path clientDuFront() {
        // Surefire s'exécute depuis le module backend ; le dépôt place le front
        // à côté. Les deux chemins couvrent « mvn -f backend » et la racine.
        for (Path candidat : List.of(
                Path.of("../frontend/src/api.ts"),
                Path.of("v2/frontend/src/api.ts"),
                Path.of("frontend/src/api.ts"))) {
            if (Files.exists(candidat)) {
                return candidat;
            }
        }
        throw new IllegalStateException(
                "client d'API du front introuvable depuis " + Path.of("").toAbsolutePath()
              + " — ce test doit échouer plutôt que de ne rien vérifier");
    }

    /** {@code /a/${x}/b} et {@code /a/{id}/b} désignent la même route. */
    private static String gabarit(String chemin) {
        String normalise = chemin
                // La chaîne de requête ne fait pas partie de l'identité d'une
                // route : le serveur déclare `GET /api/passations` et lit
                // `associationId` en @RequestParam, tandis que le front écrit
                // `/api/passations?associationId=…`. Sans cette ligne, toute
                // route à paramètre de requête était signalée « fantôme » —
                // c'est arrivé au premier appel de ce genre dans le dépôt.
                .replaceAll("\\?.*$", "")
                .replaceAll("\\$\\{[^}]*}", "{}")
                .replaceAll("\\{[^}]*}", "{}")
                .replaceAll("/{2,}", "/");
        return normalise.length() > 1 && normalise.endsWith("/")
                ? normalise.substring(0, normalise.length() - 1)
                : normalise;
    }
}
