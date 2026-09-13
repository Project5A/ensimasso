package fr.ensim.asso;

import fr.ensim.asso.adhesion.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Le cycle de vie d'une adhésion, et l'idempotence de son activation.
 *
 * <p>L'activation est déclenchée par un webhook de paiement. Les webhooks sont
 * réessayés : une activation non idempotente produit soit une erreur visible
 * par l'étudiant qui a payé, soit un double octroi. Les deux sont inacceptables.
 */
class AdhesionTest {

    private static final java.time.OffsetDateTime LE_JOUR =
            java.time.OffsetDateTime.parse("2026-09-01T10:00:00Z");

    private static final UUID PERSONNE = UUID.randomUUID();
    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID MANDAT = UUID.randomUUID();
    private static final UUID TARIF = UUID.randomUUID();

    private static OffsetDateTime le(int a, int m, int j) {
        return OffsetDateTime.of(a, m, j, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private Adhesion payante() {
        return new Adhesion(PERSONNE, ASSO, "2026-2027", MANDAT, TARIF, 1500, LE_JOUR);
    }

    @Test
    @DisplayName("une adhésion payante naît en attente de paiement")
    void naissanceEnAttente() {
        Adhesion a = payante();

        assertThat(a.getStatut()).isEqualTo(StatutAdhesion.EN_ATTENTE_PAIEMENT);
        assertThat(a.estActive()).isFalse();
        assertThat(a.getMontantPayeCents()).isEqualTo(1500);
    }

    @Test
    @DisplayName("une adhésion gratuite est active immédiatement")
    void adhesionGratuite() {
        Adhesion a = new Adhesion(PERSONNE, ASSO, "2026-2027", MANDAT, TARIF, 0, LE_JOUR);

        assertThat(a.getStatut()).isEqualTo(StatutAdhesion.ACTIVE);
        // Datée de l'horloge FOURNIE, pas de celle de la machine : c'était le
        // seul chemin du module à appeler OffsetDateTime.now() directement.
        assertThat(a.getActiveeLe()).isEqualTo(LE_JOUR);
    }

    @Test
    @DisplayName("l'activation par paiement rend l'adhésion active")
    void activationParPaiement() {
        Adhesion a = payante();

        a.activer("pi_3ABC", le(2026, 7, 15));

        assertThat(a.estActive()).isTrue();
        assertThat(a.getPaiementRef()).isEqualTo("pi_3ABC");
        assertThat(a.getActiveeLe()).isEqualTo(le(2026, 7, 15));
    }

    @Test
    @DisplayName("rejouer le MÊME paiement est sans effet — les webhooks sont réessayés")
    void activationIdempotente() {
        Adhesion a = payante();
        a.activer("pi_3ABC", le(2026, 7, 15));

        assertThatCode(() -> a.activer("pi_3ABC", le(2026, 7, 16)))
                .as("un webhook Stripe réessayé ne doit ni échouer ni modifier l'état")
                .doesNotThrowAnyException();

        assertThat(a.getActiveeLe())
                .as("la date d'activation d'origine est conservée")
                .isEqualTo(le(2026, 7, 15));
    }

    @Test
    @DisplayName("activer avec un AUTRE paiement est refusé : ce serait un double encaissement")
    void doubleActivationRefusee() {
        Adhesion a = payante();
        a.activer("pi_3ABC", le(2026, 7, 15));

        assertThatThrownBy(() -> a.activer("pi_3XYZ", le(2026, 7, 20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà active");
    }

    @Test
    @DisplayName("une adhésion active se rembourse, elle ne s'annule pas")
    void annulationApresPaiementRefusee() {
        Adhesion a = payante();
        a.activer("pi_3ABC", le(2026, 7, 15));

        assertThatThrownBy(a::annuler)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rembourse");

        assertThatCode(a::rembourser).doesNotThrowAnyException();
        assertThat(a.getStatut()).isEqualTo(StatutAdhesion.REMBOURSEE);
    }

    @Test
    @DisplayName("ce que l'adhésion couvre est distinct du bureau qui l'a vendue")
    void couvertureDistincteDuVendeur() {
        UUID bureau2025 = UUID.randomUUID();
        // Vendue en juillet 2026, par le bureau 2025-2026 alors en fonction,
        // pour l'année 2026-2027 : c'est l'« early bird ». Le bureau vendeur
        // est ici passé à la main — quel bureau le SERVICE choisit, et pourquoi
        // ce n'est pas celui qui a ouvert la campagne, est vérifié par
        // VenteAdhesionTest.
        Adhesion a = new Adhesion(PERSONNE, ASSO, "2026-2027", bureau2025, TARIF, 1500, LE_JOUR);

        assertThat(a.getCouvreAnneeCode())
                .as("la vérité pour le contrôle d'accès")
                .isEqualTo("2026-2027");
        assertThat(a.getVendueParMandatId())
                .as("l'audit : quel bureau a encaissé")
                .isEqualTo(bureau2025);
    }

    @Test
    @DisplayName("une campagne n'accepte des adhésions que dans sa fenêtre")
    void fenetreDeCampagne() {
        CampagneAdhesion c = new CampagneAdhesion(ASSO, "2026-2027", MANDAT);

        assertThat(c.accepteAdhesionA(le(2026, 7, 15)))
                .as("une campagne en brouillon n'accepte rien")
                .isFalse();

        c.ouvrir(le(2026, 7, 1), le(2026, 10, 31));

        assertThat(c.accepteAdhesionA(le(2026, 6, 30))).isFalse();
        assertThat(c.accepteAdhesionA(le(2026, 7, 15))).isTrue();
        assertThat(c.accepteAdhesionA(le(2026, 11, 1))).isFalse();

        c.fermer(le(2026, 9, 1));
        assertThat(c.accepteAdhesionA(le(2026, 8, 15)))
                .as("une campagne fermée n'accepte plus rien, même dans l'ancienne fenêtre")
                .isFalse();
    }

    @Test
    @DisplayName("une campagne fermée ne se rouvre pas")
    void reouvertureRefusee() {
        CampagneAdhesion c = new CampagneAdhesion(ASSO, "2026-2027", MANDAT);
        c.ouvrir(le(2026, 7, 1), null);
        c.fermer(le(2026, 10, 1));

        assertThatThrownBy(() -> c.ouvrir(le(2026, 10, 2), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne se rouvre pas");
    }

    @Test
    @DisplayName("un tarif négatif est refusé à la construction")
    void tarifNegatifRefuse() {
        assertThatThrownBy(() -> new TarifAdhesion(UUID.randomUUID(), "Bug", -100, PublicCible.ETUDIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("négatif");
    }
}
