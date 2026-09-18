package fr.ensim.asso;

import fr.ensim.asso.adhesion.app.ServiceAdhesion;
import fr.ensim.asso.adhesion.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * À quel bureau appartient une adhésion vendue.
 *
 * <p>Deux colonnes répondent à deux questions, et le modèle le dit lui-même :
 * {@code couvre_annee_code} est ce que l'adhésion donne droit à vivre,
 * {@code vendue_par_mandat_id} est « l'audit : quel bureau a encaissé ». Le
 * service écrivait dans la seconde le bureau qui avait OUVERT LA CAMPAGNE.
 *
 * <p>Les deux ne coïncident que si aucune passation ne sépare l'ouverture de
 * la vente — c'est-à-dire dans tous les cas SAUF celui pour lequel la
 * distinction a été introduite. Une campagne « early bird » ouverte en juillet
 * par le bureau sortant reste ouverte à la rentrée : chaque adhésion vendue en
 * septembre était portée au compte d'un bureau qui n'était plus en fonction.
 *
 * <p>Le test d'entité existant ne pouvait pas le voir : il construisait
 * l'adhésion en lui passant le mandat à la main. C'est le SERVICE qui choisit.
 */
class VenteAdhesionTest {

    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID PERSONNE = UUID.randomUUID();
    private static final UUID CAMPAGNE = UUID.randomUUID();
    private static final Set<String> ETUDIANT = Set.of("STUDENT");

    /** La rentrée : la campagne de juillet est encore ouverte. */
    private static final Instant SEPTEMBRE = Instant.parse("2026-09-15T10:00:00Z");

    private AnneeUniversitaireRepository annees;
    private CampagneAdhesionRepository campagnes;
    private TarifAdhesionRepository tarifs;
    private AdhesionRepository adhesions;
    private MandatRepository mandats;
    private ServiceAdhesion service;

    private Mandat bureauSortant;
    private Mandat bureauEntrant;
    private CampagneAdhesion campagne;

    @BeforeEach
    void avant() {
        campagnes = mock(CampagneAdhesionRepository.class);
        tarifs = mock(TarifAdhesionRepository.class);
        adhesions = mock(AdhesionRepository.class);
        mandats = mock(MandatRepository.class);

        annees = mock(AnneeUniversitaireRepository.class);

        service = new ServiceAdhesion(campagnes, tarifs, adhesions, mandats,
                annees, mock(PolitiqueAcces.class),
                new SimpleMeterRegistry(), Clock.fixed(SEPTEMBRE, ZoneOffset.UTC));

        bureauSortant = mandat("2025-2026");
        bureauEntrant = mandat("2026-2027");

        // Ouverte en juillet par le bureau sortant, pour l'année suivante :
        // c'est l'« early bird », le cas que le modèle existe pour représenter.
        campagne = new CampagneAdhesion(ASSO, "2026-2027", bureauSortant.getId());
        campagne.ouvrir(OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-10-31T00:00:00Z"));

        when(campagnes.findById(any())).thenReturn(Optional.of(campagne));
        when(tarifs.findByCampagneIdAndPublicCible(any(), eq(PublicCible.ETUDIANT)))
                .thenReturn(Optional.of(new TarifAdhesion(CAMPAGNE, "Étudiant", 1500,
                        PublicCible.ETUDIANT)));
        when(adhesions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** Un mandat porteur d'un identifiant, comme il revient de la base. */
    private Mandat mandat(String annee) {
        Mandat m = Mandat.enPreparation(ASSO, annee,
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                OffsetDateTime.parse("2027-07-01T00:00:00Z"));
        try {
            var champ = Mandat.class.getDeclaredField("id");
            champ.setAccessible(true);
            champ.set(m, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return m;
    }

    @Test
    @DisplayName("l'adhésion est portée au compte du bureau EN FONCTION à la vente")
    void venduePparLeBureauDuJour() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));

        Adhesion vendue = service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT);

        assertThat(vendue.getVendueParMandatId())
                .as("le bureau qui encaisse en septembre est le bureau entrant, "
                  + "pas celui qui a ouvert la campagne en juillet")
                .isEqualTo(bureauEntrant.getId());
        assertThat(vendue.getVendueParMandatId()).isNotEqualTo(bureauSortant.getId());

        // Et ce qu'elle couvre ne bouge pas : c'est l'autre question.
        assertThat(vendue.getCouvreAnneeCode()).isEqualTo("2026-2027");
    }

    @Test
    @DisplayName("vendue avant la passation, elle reste au compte du bureau sortant")
    void venduePparLeBureauSortantAvantLaPassation() {
        // Même campagne, même code : c'est l'instant de la vente qui décide.
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauSortant));

        assertThat(service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT)
                .getVendueParMandatId())
                .isEqualTo(bureauSortant.getId());
    }

    @Test
    @DisplayName("sans bureau en fonction, personne ne peut encaisser : la vente est refusée")
    void venteSansBureau() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.empty());

        // Cohérent avec `ouvrirCampagne`, qui refuse déjà d'ouvrir une campagne
        // sans bureau en fonction. Écrire l'adhésion au compte d'un bureau
        // arbitraire serait pire que la refuser.
        assertThatThrownBy(() ->
                service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("encaisser");
        verify(adhesions, never()).save(any());
    }

    @Test
    @DisplayName("rouvrir une campagne existante n'en crée pas une seconde, et ne réécrit pas son ouvreur")
    void reouvertureParUnAutreBureau() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));
        when(annees.findById("2026-2027")).thenReturn(Optional.of(
                new AnneeUniversitaire("2026-2027",
                        java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2027, 8, 31))));
        when(campagnes.findByAssociationIdAndCouvreAnneeCode(ASSO, "2026-2027"))
                .thenReturn(Optional.of(campagne));

        CampagneAdhesion rouverte = service.ouvrirCampagne(UUID.randomUUID(), ASSO, "2026-2027",
                OffsetDateTime.parse("2026-11-30T00:00:00Z"));

        assertThat(rouverte).isSameAs(campagne);
        verify(campagnes, never()).save(any());
        // La colonne est immuable et enregistre qui a OUVERT, pas qui a rouvert
        // en dernier. Ce qui devait suivre le bureau du jour, c'est
        // l'attribution de chaque vente — et elle est désormais lue à la vente.
        assertThat(rouverte.getOuvertePparMandatId()).isEqualTo(bureauSortant.getId());
        assertThat(rouverte.getFermeLe()).isEqualTo(OffsetDateTime.parse("2026-11-30T00:00:00Z"));
    }

    // ------------------------------------------------ réadhérer après coup

    /**
     * Les adhésions déjà en base, et la règle « laquelle occupe la place ».
     *
     * <p>Le filtre est appliqué ICI, à partir du statut, plutôt que rendu
     * ligne par ligne : un test qui répondrait {@code Optional.empty()} à la
     * main se donnerait sa propre réponse. La moitié base de cette règle —
     * l'index partiel {@code adhesion_une_vivante_par_annee} — est démontrée
     * par V12, qui rejoue les trois cas au moment même où elle la pose.
     */
    private void dejaEnBase(Adhesion... existantes) {
        when(adhesions.adhesionVivante(any(), any(), any())).thenAnswer(i ->
                java.util.Arrays.stream(existantes)
                        .filter(a -> a.getStatut() == StatutAdhesion.ACTIVE
                                  || a.getStatut() == StatutAdhesion.EN_ATTENTE_PAIEMENT)
                        .findFirst());
    }

    private Adhesion adhesion(StatutAdhesion statut) {
        Adhesion a = new Adhesion(PERSONNE, ASSO, "2026-2027", bureauSortant.getId(),
                CAMPAGNE, 1500, OffsetDateTime.parse("2026-07-10T00:00:00Z"));
        if (statut == StatutAdhesion.ACTIVE || statut == StatutAdhesion.REMBOURSEE) {
            a.activer("pi_ancien", OffsetDateTime.parse("2026-07-10T00:00:00Z"));
        }
        if (statut == StatutAdhesion.REMBOURSEE) {
            a.rembourser();
        }
        return a;
    }

    @Test
    @DisplayName("remboursé, on peut réadhérer : le remboursement rend l'argent, il ne ferme pas l'année")
    void readhesionApresRemboursement() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));
        dejaEnBase(adhesion(StatutAdhesion.REMBOURSEE));

        // La recherche portait sur TOUS les statuts : la ligne remboursée
        // suffisait à refuser, et un statut ne se périme pas. L'étudiant était
        // exclu de cette association pour l'année entière, sans autre recours
        // qu'un DELETE à la main en base.
        Adhesion nouvelle = service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT);

        assertThat(nouvelle.getStatut()).isEqualTo(StatutAdhesion.EN_ATTENTE_PAIEMENT);
        assertThat(nouvelle.getCouvreAnneeCode()).isEqualTo("2026-2027");
    }

    @Test
    @DisplayName("déjà adhérent, on ne rachète pas : le filet contre le double droit tient")
    void deuxiemeAdhesionRefusee() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));
        dejaEnBase(adhesion(StatutAdhesion.ACTIVE));

        assertThatThrownBy(() ->
                service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("déjà adhérent");
        verify(adhesions, never()).save(any());
    }

    @Test
    @DisplayName("un paiement en attente renvoie vers la commande en cours, pas vers une seconde")
    void paiementEnAttenteRenvoieVersLaCommande() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));
        dejaEnBase(adhesion(StatutAdhesion.EN_ATTENTE_PAIEMENT));

        // Ouvrir une seconde intention de paiement pour la même année, ce
        // serait rendre payable deux fois ce qui ne se doit qu'une.
        assertThatThrownBy(() ->
                service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("attend déjà son paiement");
        verify(adhesions, never()).save(any());
    }

    @Test
    @DisplayName("abandonnée faute de paiement, la place se libère aussi")
    void readhesionApresAbandon() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(bureauEntrant));
        Adhesion abandonnee = adhesion(StatutAdhesion.EN_ATTENTE_PAIEMENT);
        when(adhesions.findById(any())).thenReturn(Optional.of(abandonnee));

        service.abandonner(UUID.randomUUID());
        assertThat(abandonnee.getStatut()).isEqualTo(StatutAdhesion.ANNULEE);

        // Et abandonner deux fois n'est pas une faute.
        service.abandonner(UUID.randomUUID());
        assertThat(abandonnee.getStatut()).isEqualTo(StatutAdhesion.ANNULEE);

        dejaEnBase(abandonnee);
        assertThat(service.adherer(PERSONNE, ETUDIANT, CAMPAGNE, PublicCible.ETUDIANT)
                .getStatut()).isEqualTo(StatutAdhesion.EN_ATTENTE_PAIEMENT);
    }

    @Test
    @DisplayName("une adhésion ACTIVE ne s'abandonne pas : elle se rembourse")
    void abandonDUneAdhesionActive() {
        Adhesion active = adhesion(StatutAdhesion.ACTIVE);
        when(adhesions.findById(any())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.abandonner(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("se rembourse");
        assertThat(active.getStatut()).isEqualTo(StatutAdhesion.ACTIVE);
    }
}
