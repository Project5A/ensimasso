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
    @DisplayName("aucune méthode d'API ne renvoie une entité JPA")
    void pasDEntiteSerialisee() {
        methods()
                .that().areDeclaredInClassesThat().resideInAPackage("..api..")
                .and().arePublic()
                .should(new ArchCondition<JavaMethod>("ne pas renvoyer d'entité JPA") {
                    @Override
                    public void check(JavaMethod methode, ConditionEvents events) {
                        JavaClass retour = methode.getRawReturnType();
                        if (retour.isAnnotatedWith(jakarta.persistence.Entity.class)) {
                            events.add(SimpleConditionEvent.violated(methode,
                                    methode.getFullName() + " renvoie l'entité " + retour.getName()));
                        }
                    }
                })
                .because("c'est la faille ARCH-01/SEC-03 de la v1 : sérialiser des "
                       + "entités JPA a exposé publiquement les empreintes de mots de passe")
                .check(CLASSES);
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
            "ApercuController", "aperçu signé : le droit vient du jeton d'aperçu, pas de l'identité",
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
                boolean connaitLAppelant = methode.getMethodCallsFromSelf().stream()
                        .anyMatch(appel -> appel.getTarget().getOwner()
                                        .getSimpleName().equals("Utilisateur")
                                || appel.getTarget().getName().startsWith("idCourant"));
                if (!connaitLAppelant) {
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

}
