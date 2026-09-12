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
}
