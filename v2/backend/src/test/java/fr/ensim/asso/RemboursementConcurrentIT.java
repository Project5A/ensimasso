package fr.ensim.asso;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.tresorerie.app.ServiceTresorerie;
import fr.ensim.asso.tresorerie.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Deux remboursements en même temps, contre une vraie base.
 *
 * <p>{@code rembourser} lisait la commande SANS verrou, appelait le
 * prestataire, écrivait au journal, et n'appelait {@code commande.rembourser()}
 * — le seul contrôle d'état qui existe — qu'à la toute fin. Deux appels
 * simultanés, ce qu'un double clic suffit à produire, lisaient donc tous deux
 * une commande PAYEE : chacun écrivait sa SORTIE, chacun passait la commande en
 * REMBOURSEE dans son propre instantané, et aucun ne voyait l'autre.
 *
 * <p>Le journal comptait alors deux fois un remboursement qui n'a eu lieu
 * qu'une, et le solde de l'association était faux d'autant — définitivement,
 * car un journal ne se corrige pas en effaçant une ligne. La clé d'idempotence
 * protégeait l'argent CHEZ Stripe ; elle ne protégeait pas le journal ici.
 *
 * <p>Ce cas ne peut PAS s'écrire avec des doubles : il n'existe que parce que
 * deux transactions PostgreSQL voient deux instantanés. C'est aussi pour ça
 * qu'il n'avait jamais été écrit.
 */
class RemboursementConcurrentIT extends BaseIT {

    /** Le prestataire, remplacé : aucun euro ne part chez Stripe depuis un test. */
    @MockitoBean
    private PortPaiement prestataire;

    /** La politique d'accès, remplacée : le sujet ici est la concurrence. */
    @MockitoBean
    private PolitiqueAcces politique;

    @Autowired private ServiceTresorerie tresorerie;
    @Autowired private CommandeRepository commandes;
    @Autowired private EcritureLedgerRepository ledger;
    @Autowired private PlatformTransactionManager transactions;

    private UUID associationId;
    private UUID commandeId;

    /** Comptée dès que le premier remboursement est ENTRÉ chez le prestataire. */
    private CountDownLatch premierEngage;
    private AtomicInteger appelsPrestataire;

    @BeforeEach
    void jeuDEssai() {
        viderLesTables();
        associationId = UUID.randomUUID();
        commandeId = UUID.randomUUID();
        premierEngage = new CountDownLatch(1);
        appelsPrestataire = new AtomicInteger();

        // Le premier appel retient la main le temps que le second remboursement
        // ait eu l'occasion de charger la commande. Sans cette retenue, le
        // premier committerait avant que le second ne lise, et le défaut — qui
        // est un défaut d'INSTANTANÉS — ne se produirait pas.
        doAnswer(invocation -> {
            if (appelsPrestataire.incrementAndGet() == 1) {
                premierEngage.countDown();
                Thread.sleep(500);
            }
            return null;
        }).when(prestataire).rembourser(anyString(), anyInt());

        // Tout le jeu d'essai dans UNE transaction : le trigger de cohérence
        // « en-tête = somme des lignes » est DIFFÉRÉ, donc une commande
        // committée avant ses lignes est refusée.
        new TransactionTemplate(transactions).executeWithoutResult(statut -> {
            jdbc.update("INSERT INTO association (id, slug, nom, type_asso) VALUES (?,?,?,?)",
                    associationId, "bde", "Bureau des Élèves", "BUREAU");
            jdbc.update("INSERT INTO annee_universitaire (code, debut, fin) VALUES (?,?,?)",
                    "2026-2027", java.sql.Date.valueOf("2026-09-01"),
                    java.sql.Date.valueOf("2027-08-31"));

            UUID mandatId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut)
                    VALUES (?,?,?,?::timestamptz,?::timestamptz,'EN_FONCTION')
                    """, mandatId, associationId, "2026-2027",
                    "2026-09-01T00:00:00Z", "2027-08-31T23:59:59Z");

            UUID adhesionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO adhesion (id, personne_id, association_id, couvre_annee_code,
                                          vendue_par_mandat_id, montant_paye_cents, statut,
                                          paiement_ref, activee_le)
                    VALUES (?,?,?,?,?,1500,'ACTIVE','pi_concurrence', now())
                    """, adhesionId, UUID.randomUUID(), associationId, "2026-2027", mandatId);

            // OUVERTE d'abord : un trigger fige les lignes d'une commande PAYEE.
            // La commande passe donc payée une fois sa ligne posée, comme dans
            // la vraie vie.
            jdbc.update("""
                    INSERT INTO commande (id, personne_id, association_id, statut,
                                          montant_total_cents, devise, intention_ref)
                    VALUES (?,?,?,'OUVERTE',1500,'EUR','pi_concurrence')
                    """, commandeId, UUID.randomUUID(), associationId);
            jdbc.update("""
                    INSERT INTO ligne_commande (id, commande_id, type_ligne, reference_id,
                                                libelle, montant_cents)
                    VALUES (?,?,'ADHESION',?,'Adhésion 2026-2027',1500)
                    """, UUID.randomUUID(), commandeId, adhesionId);
            jdbc.update("UPDATE commande SET statut='PAYEE', payee_le=now() WHERE id=?",
                    commandeId);
            jdbc.update("""
                    INSERT INTO paiement (id, commande_id, fournisseur, reference,
                                          montant_cents, devise, statut)
                    VALUES (?,?,'STRIPE','pi_concurrence',1500,'EUR','REUSSI')
                    """, UUID.randomUUID(), commandeId);
        });
    }

    @Test
    @DisplayName("deux remboursements simultanés n'écrivent qu'UNE sortie au journal")
    void deuxRemboursementsSimultanes() throws Exception {
        AtomicReference<Throwable> echecPremier = new AtomicReference<>();
        AtomicReference<Throwable> echecSecond = new AtomicReference<>();

        Thread premier = new Thread(() -> {
            try {
                tresorerie.rembourser(UUID.randomUUID(), commandeId, "premier");
            } catch (Throwable t) {
                echecPremier.set(t);
            }
        }, "remboursement-1");

        Thread second = new Thread(() -> {
            try {
                // On n'entre qu'une fois le premier engagé chez le prestataire.
                premierEngage.await(5, TimeUnit.SECONDS);
                tresorerie.rembourser(UUID.randomUUID(), commandeId, "second");
            } catch (Throwable t) {
                echecSecond.set(t);
            }
        }, "remboursement-2");

        premier.start();
        second.start();
        premier.join(30_000);
        second.join(30_000);

        List<EcritureLedger> ecritures = ledger.findByAssociationIdOrderByCreeLeDesc(associationId);
        List<EcritureLedger> sorties = ecritures.stream()
                .filter(e -> e.getSens() == EcritureLedger.Sens.SORTIE)
                .toList();

        // LA vérification. Sans verrou ni contrôle d'état préalable, il y en
        // avait deux — pour un seul remboursement réellement effectué.
        assertThat(sorties)
                .as("un remboursement, une sortie : le journal ne se corrige pas après coup")
                .hasSize(1);
        assertThat(sorties.get(0).getMontantCents()).isEqualTo(1500);

        // Exactement un des deux doit avoir échoué, et pour la bonne raison.
        assertThat(List.of(echecPremier, echecSecond).stream()
                .filter(r -> r.get() != null).count())
                .as("le second remboursement doit être REFUSÉ, pas silencieusement doublé")
                .isEqualTo(1L);
        Throwable refus = echecPremier.get() != null ? echecPremier.get() : echecSecond.get();
        assertThat(refus).isInstanceOf(IllegalStateException.class);
        assertThat(refus.getMessage()).contains("payée");

        assertThat(commandes.findById(commandeId).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.REMBOURSEE);
    }
}
