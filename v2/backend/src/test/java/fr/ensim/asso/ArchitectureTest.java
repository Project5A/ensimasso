package fr.ensim.asso;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Les frontières de modules sont vérifiées à la compilation, pas en revue de
 * code. C'est ce qui rend crédible la promesse « promouvoir un module en
 * service séparé est un changement de déploiement » : si les dépendances
 * dérivaient en silence, elle serait fausse le jour où on en aurait besoin.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("fr.ensim.asso");

    @Test
    @DisplayName("les modules respectent leurs dépendances déclarées")
    void modulesValides() {
        ApplicationModules.of(EnsimAssoApplication.class).verify();
    }

    @Test
    @DisplayName("gouvernance ne dépend d'aucun autre module métier")
    void gouvernanceEstLaBase() {
        noClasses()
                .that().resideInAPackage("fr.ensim.asso.gouvernance..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("fr.ensim.asso.contenu..",
                                    "fr.ensim.asso.adhesion..",
                                    "fr.ensim.asso.passation..")
                .because("gouvernance est la couche dont tous les autres modules "
                       + "dérivent leurs droits : en dépendre créerait un cycle")
                .check(CLASSES);
    }

    @Test
    @DisplayName("aucune méthode d'API ne renvoie une entité JPA, même enveloppée")
    void pasDEntiteSerialisee() {
        // Deux angles morts, tous deux vérifiés en les provoquant.
        //
        // 1. La règle ne regardait que le type de retour BRUT. `List<Entite>`
        //    a pour type brut `List` : une collection d'entités passait donc
        //    sans être vue, et c'est la forme la plus courante d'un contrôleur.
        //    `ResponseEntity<List<Entite>>` de même.
        //
        // 2. Elle ne visait que le paquet `..api..`. Quatre classes qui
        //    sérialisent des réponses vivent ailleurs — PortailController,
        //    ApercuController, PassationController et GestionnaireErreurs — et
        //    PortailController est précisément le chemin PUBLIC, celui où une
        //    entité fuitée ferait le plus de dégâts. C'est là qu'était la
        //    faille de la v1 : des entités JPA sérialisées publiquement, avec
        //    les empreintes de mots de passe.
        //
        // Vérifié en posant une fuite `List<ThemeVersion>` dans le paquet du
        // portail : l'ancienne règle restait VERTE, la nouvelle la signale.
        //
        // On vise donc ce qu'on veut réellement couvrir — tout ce qui répond à
        // une requête HTTP — et on descend dans les types génériques.
        methods()
                .that().areDeclaredInClassesThat()
                        .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .or().areDeclaredInClassesThat()
                        .areAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class)
                .and().arePublic()
                .should(new ArchCondition<JavaMethod>("ne pas renvoyer d'entité JPA, même enveloppée") {
                    @Override
                    public void check(JavaMethod methode, ConditionEvents events) {
                        for (JavaClass type : typesTraverses(methode.getReturnType())) {
                            if (type.isAnnotatedWith(jakarta.persistence.Entity.class)) {
                                events.add(SimpleConditionEvent.violated(methode,
                                        methode.getFullName() + " renvoie l'entité "
                                      + type.getName()));
                            }
                        }
                    }
                })
                .because("c'est la faille ARCH-01/SEC-03 de la v1 : sérialiser des "
                       + "entités JPA a exposé publiquement les empreintes de mots de passe")
                .check(CLASSES);
    }

    /** Le type lui-même et tous ses arguments génériques, en profondeur. */
    private static List<JavaClass> typesTraverses(com.tngtech.archunit.core.domain.JavaType type) {
        List<JavaClass> trouves = new ArrayList<>();
        trouves.add(type.toErasure());
        if (type instanceof com.tngtech.archunit.core.domain.JavaParameterizedType parametre) {
            for (var argument : parametre.getActualTypeArguments()) {
                trouves.addAll(typesTraverses(argument));
            }
        }
        return trouves;
    }

    @Test
    @DisplayName("aucun contrôleur n'appelle directement un dépôt de contenu")
    void contenuPasseParLeService() {
        noClasses()
                .that().resideInAPackage("fr.ensim.asso.contenu.api..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("l'autorisation vit dans le service : court-circuiter la "
                       + "couche applicative contournerait PolitiqueAcces")
                .check(CLASSES);
    }

    /**
     * Routes qui n'ont légitimement pas besoin de savoir QUI appelle.
     *
     * <p>La liste est volontairement courte et nominative. Chaque entrée est
     * une décision, pas un oubli : c'est précisément la différence qui
     * manquait — quatre routes de lecture n'exerçaient aucune autorisation,
     * sans que rien ni personne n'ait jamais décidé qu'elles n'en avaient pas
     * besoin.
     */
    private static final java.util.Map<String, String> ROUTES_SANS_IDENTITE = java.util.Map.of(
            "PortailController", "chemin public : ne sert que du contenu PUBLIÉ",
            "WebhookController", "l'identité est prouvée par la signature cryptographique de Stripe",
            "ContenuController.catalogue", "catalogue des types de blocs : aucune donnée d'association",
            "GouvernanceController.lister", "annuaire des associations, déjà public sur le portail",
            "GouvernanceController.mandatsDe", "liste des mandats d'une association, déjà publique",
            "GouvernanceController.creer", "création d'association : rôle global de plateforme",
            "AdhesionController.tarifs", "grille tarifaire d'une campagne : c'est ce qu'on montre "
                    + "à qui s'apprête à adhérer, la cacher au futur adhérent n'aurait pas de sens");

    @Test
    @DisplayName("toute route qui agit ou lit au nom de quelqu'un sait qui c'est")
    void routesConnaissentLeurAppelant() {
        List<String> aveugles = new ArrayList<>();

        for (JavaClass classe : CLASSES) {
            if (!classe.getSimpleName().endsWith("Controller")
                    || ROUTES_SANS_IDENTITE.containsKey(classe.getSimpleName())) {
                continue;
            }
            for (JavaMethod methode : classe.getMethods()) {
                boolean estUneRoute = methode.getAnnotations().stream()
                        .anyMatch(a -> a.getRawType().getSimpleName().endsWith("Mapping"));
                if (!estUneRoute
                        || ROUTES_SANS_IDENTITE.containsKey(
                                classe.getSimpleName() + "." + methode.getName())) {
                    continue;
                }
                if (!connaitLAppelant(methode)) {
                    aveugles.add(classe.getSimpleName() + "." + methode.getName());
                }
            }
        }

        // Quatre routes de lecture n'exerçaient AUCUNE autorisation : la liste
        // des pages d'un mandat et les blocs d'une version — brouillons du
        // bureau entrant compris —, la signature d'une URL de lecture pour
        // n'importe quelle clé de média, et la composition d'un bureau avec les
        // identifiants Keycloak de ses membres. Tout compte authentifié y avait
        // accès, pour n'importe quelle association.
        assertThat(aveugles)
                .as("ces routes ne savent pas au nom de qui elles répondent : "
                  + "soit elles exigent une identité, soit elles rejoignent "
                  + "ROUTES_SANS_IDENTITE avec la raison écrite")
                .isEmpty();
    }

    @Test
    @DisplayName("aucune dispense inutile : une entrée qui ne sert plus est un trou qui attend")
    void dispensesToutesNecessaires() {
        // ApercuController figurait dans la liste au motif d'un « jeton
        // d'aperçu » qui n'existe nulle part — et il appelle pourtant
        // Utilisateur.idCourantObligatoire(), donc il passait la règle sans
        // dispense. Une dispense dont on n'a pas besoin est pire qu'un
        // commentaire faux : elle reste ouverte, et le jour où quelqu'un
        // retire le contrôle d'identité de cette route, la règle ne bronche
        // pas. Ce cas retire la dispense dès qu'elle cesse d'être nécessaire.
        List<String> inutiles = new ArrayList<>();

        for (JavaClass classe : CLASSES) {
            if (!classe.getSimpleName().endsWith("Controller")) {
                continue;
            }
            List<JavaMethod> routes = classe.getMethods().stream()
                    .filter(m -> m.getAnnotations().stream()
                            .anyMatch(a -> a.getRawType().getSimpleName().endsWith("Mapping")))
                    .toList();
            if (routes.isEmpty()) {
                continue;
            }
            if (ROUTES_SANS_IDENTITE.containsKey(classe.getSimpleName())
                    && routes.stream().allMatch(ArchitectureTest::connaitLAppelant)) {
                inutiles.add(classe.getSimpleName() + " (toutes ses routes exigent déjà une identité)");
            }
            for (JavaMethod methode : routes) {
                String cle = classe.getSimpleName() + "." + methode.getName();
                if (ROUTES_SANS_IDENTITE.containsKey(cle) && connaitLAppelant(methode)) {
                    inutiles.add(cle + " (exige déjà une identité)");
                }
            }
        }

        assertThat(inutiles)
                .as("ces dispenses ne servent à rien : retirez-les de "
                  + "ROUTES_SANS_IDENTITE plutôt que de laisser la règle "
                  + "aveugle sur des routes qui, elles, se gardent bien")
                .isEmpty();
    }

    private static boolean connaitLAppelant(JavaMethod methode) {
        return methode.getMethodCallsFromSelf().stream()
                .anyMatch(appel -> appel.getTarget().getOwner().getSimpleName().equals("Utilisateur")
                        || appel.getTarget().getName().startsWith("idCourant"));
    }

    @Test
    @DisplayName("aucun usage de java.util.Date ni de java.sql.Date")
    void pasDeDatesLegacy() {
        noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.sql.Date")
                .because("la v1 utilisait java.sql.Date pour les dates d'évènement ; "
                       + "le domaine utilise java.time")
                .check(CLASSES);
    }
    @Test
    @DisplayName("un composant Spring n'a qu'un constructeur : sinon il ne démarre pas")
    void composantsSansConstructeurAmbigu() {
        // Panne réellement rencontrée : CacheMemoire avait deux constructeurs,
        // Spring n'a pas su choisir, et l'application refusait de démarrer sur
        // son profil PAR DÉFAUT. Aucun test unitaire ne l'a vu — ils
        // instancient la classe eux-mêmes — et tous les lancements manuels
        // utilisaient l'autre implémentation.
        ArchCondition<JavaClass> unSeulConstructeurInjectable =
                new ArchCondition<>("n'avoir qu'un constructeur, ou un seul annoté @Autowired") {
                    @Override
                    public void check(JavaClass classe, ConditionEvents evenements) {
                        long constructeurs = classe.getConstructors().size();
                        long annotes = classe.getConstructors().stream()
                                .filter(c -> c.isAnnotatedWith(
                                        org.springframework.beans.factory.annotation.Autowired.class))
                                .count();
                        if (constructeurs > 1 && annotes != 1) {
                            evenements.add(SimpleConditionEvent.violated(classe,
                                    classe.getName() + " déclare " + constructeurs
                                  + " constructeurs sans en désigner un : Spring ne saura pas choisir"));
                        }
                    }
                };

        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().areAnnotatedWith(org.springframework.stereotype.Component.class)
                .or().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .or().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should(unSeulConstructeurInjectable)
                .check(CLASSES);
    }


    /**
     * Une contrainte de validation qui n'est jamais évaluée.
     *
     * <p>{@code AgendaController.annuler} déclarait {@code @Size(max = 400)} sur
     * le motif d'annulation — et recevait le corps sans {@code @Valid}. Jakarta
     * Validation n'évalue RIEN sans lui : un motif de 5 000 caractères
     * traversait le contrôleur, traversait le service, et n'était refusé qu'au
     * COMMIT par le {@code CHECK} de la colonne. C'est-à-dire en 500, avec un
     * message de contrainte PostgreSQL, sur une saisie d'utilisateur.
     *
     * <p>Mesuré avant correction, via MockMvc : « POST annuler, motif de 5000
     * car -> statut 200 », et « le service a reçu un motif de 5000 caractères ».
     *
     * <p>Cette règle vaut pour tous les corps à venir : une annotation de
     * validation posée sur un record qu'on n'annote pas {@code @Valid} est une
     * garantie décorative, et c'est la pire espèce — elle se lit comme une
     * protection.
     */
    @Test
    @DisplayName("un corps de requête contraint est VALIDÉ, pas seulement annoté")
    void corpsContraintsValides() {
        List<String> decoratifs = new ArrayList<>();

        for (JavaClass classe : CLASSES) {
            if (!classe.getSimpleName().endsWith("Controller")) {
                continue;
            }
            for (JavaMethod methode : classe.getMethods()) {
                for (var parametre : methode.getParameters()) {
                    boolean estUnCorps = parametre.getAnnotations().stream()
                            .anyMatch(a -> a.getRawType().getSimpleName().equals("RequestBody"));
                    if (!estUnCorps || !porteDesContraintes(parametre.getRawType())) {
                        continue;
                    }
                    boolean valide = parametre.getAnnotations().stream()
                            .anyMatch(a -> a.getRawType().getSimpleName().equals("Valid"));
                    if (!valide) {
                        decoratifs.add(classe.getSimpleName() + "." + methode.getName()
                                + " (" + parametre.getRawType().getSimpleName() + ")");
                    }
                }
            }
        }

        assertThat(decoratifs)
                .as("ces corps déclarent des contraintes que personne n'évalue : "
                  + "ajoutez @Valid, ou retirez les annotations qui font croire "
                  + "à une protection")
                .isEmpty();
    }

    /** Le type porte-t-il au moins une annotation de Jakarta Validation ? */
    private static boolean porteDesContraintes(JavaClass type) {
        return type.getAllFields().stream()
                .flatMap(champ -> champ.getAnnotations().stream())
                .anyMatch(a -> a.getRawType().getPackageName().startsWith("jakarta.validation"));
    }
}
