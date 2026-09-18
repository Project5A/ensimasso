package fr.ensim.asso;

import fr.ensim.asso.adhesion.app.ServiceAdhesion;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.shared.error.Erreurs;
import fr.ensim.asso.tresorerie.app.ServiceTresorerie;
import fr.ensim.asso.tresorerie.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Les trois chemins de la trésorerie qui touchent à l'argent, isolés de la base.
 *
 * <p>Aucun test ne passait par {@link ServiceTresorerie} : les tests existants
 * vérifient les objets du domaine un par un. Les trois défauts couverts ici
 * vivaient tous dans l'orchestration, c'est-à-dire précisément dans l'espace
 * que personne ne regardait.
 */
class TresorerieServiceTest {

    private CommandeRepository commandes;
    private LigneCommandeRepository lignes;
    private PaiementRepository paiements;
    private EvenementStripeRepository evenements;
    private EcritureLedgerRepository ledger;
    private PortPaiement prestataire;
    private ServiceAdhesion adhesions;
    private PolitiqueAcces politique;
    private ServiceTresorerie service;

    private final UUID commandeId = UUID.randomUUID();
    private final UUID personneId = UUID.randomUUID();
    private final UUID assoId = UUID.randomUUID();

    @BeforeEach
    void montage() {
        commandes = mock(CommandeRepository.class);
        lignes = mock(LigneCommandeRepository.class);
        paiements = mock(PaiementRepository.class);
        evenements = mock(EvenementStripeRepository.class);
        ledger = mock(EcritureLedgerRepository.class);
        prestataire = mock(PortPaiement.class);
        adhesions = mock(ServiceAdhesion.class);
        politique = mock(PolitiqueAcces.class);

        when(evenements.save(any())).thenAnswer(i -> i.getArgument(0));
        when(paiements.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));

        service = new ServiceTresorerie(commandes, lignes, paiements, evenements, ledger,
                prestataire, adhesions, politique,
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * Une commande telle qu'elle revient du dépôt : avec son identifiant.
     *
     * <p>L'identifiant est généré par la base ; une entité construite à la main
     * l'a nul, et le code qui s'en sert échouerait pour une raison qui n'a rien
     * à voir avec ce qu'on teste.
     */
    private Commande commandeOuverte() {
        Commande c = new Commande(personneId, assoId, 1500, "EUR");
        c.rattacherIntention("pi_existante");
        try {
            var champ = Commande.class.getDeclaredField("id");
            champ.setAccessible(true);
            champ.set(c, commandeId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(commandes.findById(commandeId)).thenReturn(Optional.of(c));
        when(commandes.findByIdPourEcriture(commandeId)).thenReturn(Optional.of(c));
        return c;
    }

    // ------------------------------------------------------------- webhook

    @Test
    @DisplayName("un évènement de paiement illisible n'est JAMAIS marqué traité")
    void evenementIllisibleNonAcquitte() {
        var illisible = PortPaiement.EvenementRecu.illisible("evt_9", "payment_intent.succeeded");
        when(evenements.existsById("evt_9")).thenReturn(false);

        // Le défaut : referenceCommande() étant nulle, l'évènement tombait dans
        // la branche « aucune référence de commande », était marqué traité, et
        // la déduplication par identifiant interdisait tout rejeu. L'argent
        // était encaissé chez Stripe et le droit acheté jamais accordé.
        assertThatThrownBy(() -> service.traiter(illisible))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("illisible");

        verify(adhesions, never()).confirmerPaiement(any(), anyString());
    }

    @Test
    @DisplayName("un évènement LU sans référence de commande reste, lui, un évènement ignoré")
    void evenementLuSansCommandeIgnore() {
        var lu = PortPaiement.EvenementRecu.lu(
                "evt_10", "payment_intent.succeeded", "pi_x", null, 1500, "eur");
        when(evenements.existsById("evt_10")).thenReturn(false);

        // La distinction est tout l'objet du correctif : « compris, et sans
        // rapport avec nous » n'est pas « pas compris ».
        assertThat(service.traiter(lu)).isEqualTo(ServiceTresorerie.ResultatWebhook.IGNORE);
    }

    // -------------------------------------------------------- secret client

    @Test
    @DisplayName("relire le secret client ne crée pas une nouvelle intention de paiement")
    void secretClientNeCreePasDIntention() {
        commandeOuverte();
        when(prestataire.secretClientDe("pi_existante")).thenReturn(Optional.of("pi_existante_secret"));
        // Créer une intention RESTE possible : ce que le test vérifie, c'est
        // que ce chemin ne le fait pas — pas que le prestataire en soit incapable.
        when(prestataire.creerIntention(anyInt(), anyString(), anyString()))
                .thenReturn(new PortPaiement.Intention("pi_nouvelle", "pi_nouvelle_secret"));

        assertThat(service.secretClientDe(personneId, commandeId)).isEqualTo("pi_existante_secret");

        verify(prestataire).secretClientDe("pi_existante");
        verify(prestataire, never()).creerIntention(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("une commande déjà payée n'ouvre plus de moyen de la repayer")
    void secretClientRefuseSurCommandePayee() {
        Commande c = commandeOuverte();
        c.marquerPayee(1500, java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z"));

        assertThatThrownBy(() -> service.secretClientDe(personneId, commandeId))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("PAYEE");

        verify(prestataire, never()).creerIntention(anyInt(), anyString(), anyString());
        verify(prestataire, never()).secretClientDe(anyString());
    }

    // -------------------------------------------------------- remboursement

    @Test
    @DisplayName("rembourser une commande reprend le droit acheté")
    void remboursementRevoqueLeDroit() {
        Commande c = commandeOuverte();
        c.marquerPayee(1500, java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z"));

        UUID adhesionId = UUID.randomUUID();
        Paiement p = new Paiement(commandeId, "STRIPE", "pi_existante", 1500, "EUR",
                Paiement.StatutPaiement.REUSSI);
        when(paiements.findByCommandeId(commandeId)).thenReturn(List.of(p));
        when(lignes.findByCommandeId(commandeId)).thenReturn(List.of(
                new LigneCommande(commandeId, TypeLigne.ADHESION, adhesionId, "Adhésion", 1500)));
        when(politique.peut(any(), any(), any())).thenReturn(true);

        service.rembourser(personneId, commandeId, "erreur de saisie");

        verify(prestataire).rembourser("pi_existante", 1500);
        // Le défaut : l'argent repartait, la commande passait en REMBOURSEE, et
        // l'adhésion restait ACTIVE. Adhesion.rembourser() existait et n'était
        // appelée de nulle part.
        verify(adhesions).revoquerPourRemboursement(adhesionId);
        assertThat(c.getStatut()).isEqualTo(StatutCommande.REMBOURSEE);

        // La commande est chargée par le finder VERROUILLANT, pas par findById.
        // Sans lui, deux remboursements simultanés lisent tous deux une
        // commande PAYEE et écrivent tous deux une SORTIE.
        verify(commandes).findByIdPourEcriture(commandeId);
        verify(commandes, never()).findById(commandeId);
    }

    @Test
    @DisplayName("une commande déjà remboursée ne repart pas chez le prestataire")
    void secondRemboursementRefuseAvantLePrestataire() {
        Commande c = commandeOuverte();
        c.marquerPayee(1500, java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z"));
        c.rembourser();                                  // déjà remboursée

        Paiement p = new Paiement(commandeId, "STRIPE", "pi_existante", 1500, "EUR",
                Paiement.StatutPaiement.REUSSI);
        when(paiements.findByCommandeId(commandeId)).thenReturn(List.of(p));

        assertThatThrownBy(() -> service.rembourser(personneId, commandeId, "encore"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payée");

        // Le contrôle d'état venait APRÈS l'appel au prestataire et APRÈS
        // l'écriture au journal : le second remboursement partait chez Stripe
        // et laissait une SORTIE de plus derrière lui avant d'échouer — ou,
        // en concurrence, sans échouer du tout.
        verify(prestataire, never()).rembourser(any(), anyInt());
        verify(ledger, never()).save(any());
        verify(adhesions, never()).revoquerPourRemboursement(any());
    }

    @Test
    @DisplayName("rembourser exige la permission, et un refus arrête tout")
    void remboursementExigeLaPermission() {
        Commande c = commandeOuverte();
        c.marquerPayee(1500, java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z"));

        // Le cas précédent stubait `politique.peut(...)`, que `rembourser`
        // n'appelle jamais : il passait donc à l'identique si le contrôle de
        // permission disparaissait du service. C'est `exiger` qu'il faut
        // éprouver, et un mock ne lève rien tant qu'on ne le lui demande pas.
        doThrow(new Erreurs.AccesRefuse("pas trésorier"))
                .when(politique).exiger(personneId, Permission.FINANCE_CONSULTER, assoId);

        assertThatThrownBy(() -> service.rembourser(personneId, commandeId, "tentative"))
                .isInstanceOf(Erreurs.AccesRefuse.class);

        verify(prestataire, never()).rembourser(any(), anyInt());
        verify(ledger, never()).save(any());
        assertThat(c.getStatut()).isEqualTo(StatutCommande.PAYEE);
    }
}
