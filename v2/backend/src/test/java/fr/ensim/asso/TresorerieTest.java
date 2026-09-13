package fr.ensim.asso;

import fr.ensim.asso.tresorerie.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Le cycle de vie d'une commande et l'intégrité du journal.
 *
 * <p>Le scénario qui compte n'est pas le chemin heureux : c'est le webhook
 * rejoué, et le montant encaissé qui ne correspond pas au montant dû. Les deux
 * sont silencieux dans la v1, où aucun webhook n'existait et où le montant
 * venait du navigateur.
 */
class TresorerieTest {

    private static final UUID PERSONNE = UUID.randomUUID();
    private static final UUID ASSO = UUID.randomUUID();

    private static OffsetDateTime le(int a, int m, int j) {
        return OffsetDateTime.of(a, m, j, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private Commande commande(int montant) {
        return new Commande(PERSONNE, ASSO, montant, "EUR");
    }

    @Test
    @DisplayName("une commande naît ouverte et impayée")
    void naissanceOuverte() {
        Commande c = commande(1500);

        assertThat(c.getStatut()).isEqualTo(StatutCommande.OUVERTE);
        assertThat(c.estPayee()).isFalse();
        assertThat(c.getPayeeLe()).isNull();
    }

    @Test
    @DisplayName("le paiement du montant exact marque la commande payée")
    void paiementExact() {
        Commande c = commande(1500);

        c.marquerPayee(1500, le(2026, 7, 15));

        assertThat(c.estPayee()).isTrue();
        assertThat(c.getPayeeLe()).isEqualTo(le(2026, 7, 15));
    }

    @Test
    @DisplayName("un montant encaissé différent du montant dû est refusé")
    void montantDivergentRefuse() {
        Commande c = commande(1500);

        assertThatThrownBy(() -> c.marquerPayee(1, le(2026, 7, 15)))
                .as("c'est exactement l'attaque que permettait la v1 : {\"amount\": 1}")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("différent du montant dû");

        assertThat(c.estPayee()).isFalse();
    }

    @Test
    @DisplayName("rejouer le MÊME webhook est sans effet — Stripe réessaie")
    void webhookRejoue() {
        Commande c = commande(1500);
        c.marquerPayee(1500, le(2026, 7, 15));

        assertThatCode(() -> c.marquerPayee(1500, le(2026, 7, 15)))
                .doesNotThrowAnyException();

        assertThat(c.getPayeeLe())
                .as("la date d'encaissement d'origine est conservée")
                .isEqualTo(le(2026, 7, 15));
    }

    @Test
    @DisplayName("un rejeu avec un montant différent est un signal grave, pas un rejeu")
    void rejeuIncoherent() {
        Commande c = commande(1500);
        c.marquerPayee(1500, le(2026, 7, 15));

        assertThatThrownBy(() -> c.marquerPayee(2000, le(2026, 7, 16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("montant différent");
    }

    @Test
    @DisplayName("une commande payée se rembourse, elle ne s'annule pas")
    void annulationApresPaiement() {
        Commande c = commande(1500);
        c.marquerPayee(1500, le(2026, 7, 15));

        assertThatThrownBy(c::annuler)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rembourse");

        assertThatCode(c::rembourser).doesNotThrowAnyException();
        assertThat(c.getStatut()).isEqualTo(StatutCommande.REMBOURSEE);
    }

    @Test
    @DisplayName("on ne rattache pas d'intention à une commande déjà payée")
    void intentionApresPaiement() {
        Commande c = commande(1500);
        c.marquerPayee(1500, le(2026, 7, 15));

        assertThatThrownBy(() -> c.rattacherIntention("pi_3XYZ"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("une écriture porte un montant strictement positif ; le sens dit l'entrée ou la sortie")
    void ecritureSansMontantSigne() {
        assertThatThrownBy(() -> new EcritureLedger(UUID.randomUUID(), UUID.randomUUID(), ASSO,
                EcritureLedger.Sens.ENTREE, -500, "incohérent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictement positif");

        assertThatThrownBy(() -> new EcritureLedger(UUID.randomUUID(), UUID.randomUUID(), ASSO,
                EcritureLedger.Sens.SORTIE, 0, "vide"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("une écriture d'entrée et une de sortie s'expriment par le sens")
    void sensExplicite() {
        EcritureLedger entree = new EcritureLedger(UUID.randomUUID(), UUID.randomUUID(), ASSO,
                EcritureLedger.Sens.ENTREE, 1500, "Encaissement adhésion");
        EcritureLedger sortie = new EcritureLedger(UUID.randomUUID(), UUID.randomUUID(), ASSO,
                EcritureLedger.Sens.SORTIE, 1500, "Remboursement");

        assertThat(entree.getMontantCents()).isEqualTo(1500);
        assertThat(sortie.getMontantCents()).isEqualTo(1500);
        assertThat(entree.getSens()).isNotEqualTo(sortie.getSens());
    }

    @Test
    @DisplayName("une trace d'évènement retient ce qui a été fait")
    void traceEvenement() {
        EvenementStripe e = new EvenementStripe("evt_1ABC", "payment_intent.succeeded");

        assertThat(e.getTraiteLe()).isNull();
        e.marquerTraite("commande payée", le(2026, 7, 15));

        assertThat(e.getTraiteLe()).isEqualTo(le(2026, 7, 15));
        assertThat(e.getResultat()).isEqualTo("commande payée");
    }

    @Test
    @DisplayName("une ligne de commande porte un droit identifié, pas un montant libre")
    void ligneReferenceUnDroit() {
        UUID adhesionId = UUID.randomUUID();
        LigneCommande l = new LigneCommande(UUID.randomUUID(), TypeLigne.ADHESION,
                adhesionId, "Adhésion 2026-2027", 1500);

        assertThat(l.getTypeLigne()).isEqualTo(TypeLigne.ADHESION);
        assertThat(l.getReferenceId())
                .as("l'unicité (type, référence) en base empêche de facturer deux fois le même droit")
                .isEqualTo(adhesionId);
    }

}
