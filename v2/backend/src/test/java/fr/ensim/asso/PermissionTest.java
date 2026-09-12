package fr.ensim.asso;

import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.gouvernance.domain.Poste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La table de vérité de l'autorisation.
 *
 * <p>Dans la v1, l'autorisation n'existait que comme une liste de motifs d'URL
 * dans un fichier de configuration — intestable, et effectivement fausse : 27
 * des 32 routes étaient publiques. Ici la règle est une donnée du domaine,
 * donc elle s'énumère et se vérifie.
 */
class PermissionTest {

    @ParameterizedTest(name = "{0} est porté par {1}")
    @CsvSource({
            "PAGE_PUBLIER,      PRESIDENT",
            "PAGE_PUBLIER,      VICE_PRESIDENT",
            "PAGE_EDITER,       RESP_COM",
            "MEMBRE_GERER,      SECRETAIRE",
            "FINANCE_CONSULTER, TRESORIER",
            "EVENEMENT_GERER,   RESP_EVENEMENTS",
            "PASSATION_LANCER,  PRESIDENT",
    })
    @DisplayName("les postes attendus portent bien leurs permissions")
    void permissionsAccordees(Permission permission, Poste poste) {
        assertThat(permission.portéePar(poste)).isTrue();
    }

    @ParameterizedTest(name = "{0} n'est PAS porté par {1}")
    @CsvSource({
            // Le responsable com rédige mais ne publie pas.
            "PAGE_PUBLIER,      RESP_COM",
            // Le trésorier gère l'argent, pas le contenu.
            "PAGE_EDITER,       TRESORIER",
            "PAGE_PUBLIER,      TRESORIER",
            // Un membre du bureau sans délégation ne peut rien d'engageant.
            "PAGE_PUBLIER,      MEMBRE_BUREAU",
            "MEMBRE_GERER,      MEMBRE_BUREAU",
            "FINANCE_CONSULTER, MEMBRE_BUREAU",
            "PASSATION_LANCER,  MEMBRE_BUREAU",
            // Seul le président lance une passation.
            "PASSATION_LANCER,  VICE_PRESIDENT",
            "PASSATION_LANCER,  TRESORIER",
            "PASSATION_LANCER,  SECRETAIRE",
            // Les finances ne sont pas ouvertes à la communication.
            "FINANCE_CONSULTER, RESP_COM",
            "FINANCE_CONSULTER, SECRETAIRE",
    })
    @DisplayName("les postes non habilités sont bien refusés")
    void permissionsRefusees(Permission permission, Poste poste) {
        assertThat(permission.portéePar(poste)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("le président porte toute permission : il est responsable de l'association")
    void presidentPeutTout(Permission permission) {
        assertThat(permission.portéePar(Poste.PRESIDENT))
                .as("le président doit pouvoir %s", permission)
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("aucune permission n'est ouverte à tous les postes")
    void aucunePermissionUniverselle(Permission permission) {
        assertThat(permission.postesAutorises())
                .as("%s ne doit pas être accordée à tous les postes", permission)
                .hasSizeLessThan(Poste.values().length);
    }

    @Test
    @DisplayName("MEMBRE_BUREAU ne porte aucune permission par défaut")
    void membreSimpleNaRien() {
        for (Permission p : Permission.values()) {
            assertThat(p.portéePar(Poste.MEMBRE_BUREAU))
                    .as("MEMBRE_BUREAU ne doit pas porter %s", p)
                    .isFalse();
        }
    }
}
