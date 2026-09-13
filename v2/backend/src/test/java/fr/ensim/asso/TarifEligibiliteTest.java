package fr.ensim.asso;

import fr.ensim.asso.adhesion.domain.PublicCible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qui a droit à quel tarif.
 *
 * <p>Le corps de la requête d'adhésion ne contient volontairement aucun
 * montant — c'était la correction de la faille PAY de la v1, où
 * {@code amount} venait du navigateur. Mais il contient le PUBLIC CIBLE, qui
 * détermine le montant tout aussi sûrement, et rien ne vérifiait que
 * l'appelant y avait droit. Poster {@code {"publicCible":"ETUDIANT"}} suffisait
 * à payer le tarif étudiant.
 */
class TarifEligibiliteTest {

    @Test
    @DisplayName("le tarif étudiant demande le statut d'étudiant")
    void tarifEtudiant() {
        assertThat(PublicCible.ETUDIANT.estOuvertA(Set.of("STUDENT"))).isTrue();
        assertThat(PublicCible.ETUDIANT.estOuvertA(Set.of("ALUMNI"))).isFalse();
        assertThat(PublicCible.ETUDIANT.estOuvertA(Set.of())).isFalse();
    }

    @Test
    @DisplayName("le tarif ancien demande le statut d'ancien")
    void tarifAncien() {
        assertThat(PublicCible.ANCIEN.estOuvertA(Set.of("ALUMNI"))).isTrue();
        assertThat(PublicCible.ANCIEN.estOuvertA(Set.of("STUDENT"))).isFalse();
    }

    @Test
    @DisplayName("le tarif extérieur est ouvert à tous : c'est le plus cher")
    void tarifExterieur() {
        // Se déclarer extérieur quand on est étudiant coûte plus cher : c'est
        // un choix, pas une fraude. Rien à vérifier de ce côté-là.
        assertThat(PublicCible.EXTERIEUR.estOuvertA(Set.of())).isTrue();
        assertThat(PublicCible.EXTERIEUR.estOuvertA(Set.of("STUDENT"))).isTrue();
    }

    @Test
    @DisplayName("les rôles exigés existent réellement dans le royaume Keycloak")
    void rolesCoherentsAvecLeRoyaume() throws Exception {
        // Une règle qui exige un rôle que l'IdP ne délivre jamais refuserait
        // tout le monde, en silence. Le royaume est versionné : on le lit.
        var racine = java.nio.file.Path.of("..").resolve("infra/keycloak/realm-ensimasso.json");
        assertThat(racine).as("le royaume de développement doit être versionné").exists();
        String royaume = java.nio.file.Files.readString(racine);

        for (PublicCible cible : PublicCible.values()) {
            cible.roleRequis().ifPresent(role ->
                    assertThat(royaume)
                            .as("le rôle %s exigé par %s doit exister dans le royaume", role, cible)
                            .contains("\"" + role + "\""));
        }
    }
}
