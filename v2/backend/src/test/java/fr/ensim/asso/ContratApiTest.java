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
                .replaceAll("\\$\\{[^}]*}", "{}")
                .replaceAll("\\{[^}]*}", "{}")
                .replaceAll("/{2,}", "/");
        return normalise.length() > 1 && normalise.endsWith("/")
                ? normalise.substring(0, normalise.length() - 1)
                : normalise;
    }
}
