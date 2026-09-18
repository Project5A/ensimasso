package fr.ensim.asso.gouvernance;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * « Cette personne peut-elle faire cette action ? »
 *
 * <p>Le point unique où la question se décide, et rien ne l'éprouvait :
 * {@code PermissionTest} vérifie la table de vérité {@code Permission × Poste},
 * c'est-à-dire la moitié du problème. L'autre moitié — quel mandat accepte une
 * écriture, et l'appartenance à CE mandat-là — n'était couverte par aucun test.
 *
 * <p>C'est là que vivait une contradiction : le javadoc de la classe posait
 * comme règle « non négociable » que les écritures ne sont permises que sur le
 * mandat EN FONCTION, alors que {@code peutSurMandat} les autorise aussi sur un
 * mandat en PRÉPARATION — délibérément, puisque le bureau entrant compose son
 * site avant l'investiture. La règle appliquée était la bonne ; c'est la phrase
 * qui était fausse, sur la classe même où l'on vient vérifier ce qui est
 * permis. Ces cas fixent la règle RÉELLE, pour qu'elle ne dépende plus d'un
 * commentaire.
 */
class PolitiqueAccesTest {

    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID MANDAT = UUID.randomUUID();
    private static final UUID PERSONNE = UUID.randomUUID();

    private MandatRepository mandats;
    private MembreBureauRepository membres;
    private PolitiqueAcces politique;

    @BeforeEach
    void avant() {
        mandats = mock(MandatRepository.class);
        membres = mock(MembreBureauRepository.class);
        politique = new PolitiqueAcces(mandats, membres);
    }

    private Mandat mandat(StatutMandat statut) {
        Mandat m = Mandat.enPreparation(ASSO, "2026-2027",
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2027-08-31T00:00:00Z"));
        if (statut != StatutMandat.PREPARATION) {
            m.investir(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
        }
        if (statut == StatutMandat.CLOS) {
            m.clore(OffsetDateTime.parse("2027-08-31T00:00:00Z"));
        }
        return m;
    }

    private void siege(Poste poste) {
        when(membres.posteActif(MANDAT, PERSONNE))
                .thenReturn(Optional.of(new MembreBureau(MANDAT, PERSONNE, poste, 0)));
    }

    // ------------------------------------------- écriture sur un mandat nommé

    @Test
    @DisplayName("un mandat EN FONCTION accepte l'écriture de qui en a le poste")
    void mandatEnFonction() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.EN_FONCTION)));
        siege(Poste.PRESIDENT);

        assertThat(politique.peutSurMandat(PERSONNE, Permission.PAGE_PUBLIER, MANDAT)).isTrue();
    }

    @Test
    @DisplayName("un mandat en PRÉPARATION aussi : le bureau entrant compose son site")
    void mandatEnPreparation() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.PREPARATION)));
        siege(Poste.RESP_COM);

        // C'est la règle RÉELLE, et elle est juste : sans elle, un bureau
        // entrant ne pourrait rien préparer avant son investiture — et
        // l'aperçu d'un brouillon de préparation, qui existe, serait
        // inatteignable.
        assertThat(politique.peutSurMandat(PERSONNE, Permission.PAGE_EDITER, MANDAT)).isTrue();
    }

    @Test
    @DisplayName("un mandat CLOS n'accepte plus rien, quel que soit le poste")
    void mandatClos() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.CLOS)));
        siege(Poste.PRESIDENT);

        // L'archive est immuable : c'est ce qui rend une page de 2023 fiable
        // en 2029.
        for (Permission p : Permission.values()) {
            assertThat(politique.peutSurMandat(PERSONNE, p, MANDAT))
                    .as("%s sur un mandat clos", p)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("le poste doit porter la permission demandée")
    void posteSansLaPermission() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.EN_FONCTION)));
        siege(Poste.RESP_EVENEMENTS);

        assertThat(politique.peutSurMandat(PERSONNE, Permission.EVENEMENT_GERER, MANDAT)).isTrue();
        // Un partenariat engage l'association ; le responsable évènements n'est
        // partie à aucune des deux contreparties.
        assertThat(politique.peutSurMandat(PERSONNE, Permission.PARTENAIRE_GERER, MANDAT)).isFalse();
    }

    @Test
    @DisplayName("siéger ailleurs ne donne aucun droit ici")
    void siegeDansUnAutreMandat() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.EN_FONCTION)));
        // Président d'une AUTRE association : posteActif est interrogé pour CE
        // mandat, et ne rend rien.
        when(membres.posteActif(MANDAT, PERSONNE)).thenReturn(Optional.empty());

        assertThat(politique.peutSurMandat(PERSONNE, Permission.PAGE_PUBLIER, MANDAT)).isFalse();
    }

    @Test
    @DisplayName("un mandat inconnu, une identité absente : refusés sans interroger le bureau")
    void entreesDegradees() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.empty());
        assertThat(politique.peutSurMandat(PERSONNE, Permission.PAGE_EDITER, MANDAT)).isFalse();

        assertThat(politique.peutSurMandat(null, Permission.PAGE_EDITER, MANDAT)).isFalse();
        assertThat(politique.peutSurMandat(PERSONNE, Permission.PAGE_EDITER, null)).isFalse();
        assertThat(politique.peut(null, Permission.PAGE_EDITER, ASSO)).isFalse();
        assertThat(politique.peut(PERSONNE, Permission.PAGE_EDITER, null)).isFalse();
    }

    // ------------------------------------------ écriture au niveau association

    @Test
    @DisplayName("sans bureau en fonction, une association n'autorise rien")
    void associationSansBureau() {
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.empty());

        assertThat(politique.peut(PERSONNE, Permission.CAMPAGNE_GERER, ASSO)).isFalse();
    }

    @Test
    @DisplayName("au niveau association, c'est le bureau EN FONCTION qui décide")
    void associationAvecBureau() {
        Mandat enFonction = mandat(StatutMandat.EN_FONCTION);
        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.of(enFonction));
        when(membres.posteActif(any(), any()))
                .thenReturn(Optional.of(new MembreBureau(enFonction.getId(), PERSONNE,
                        Poste.TRESORIER, 0)));

        assertThat(politique.peut(PERSONNE, Permission.FINANCE_CONSULTER, ASSO)).isTrue();
        assertThat(politique.peut(PERSONNE, Permission.PASSATION_LANCER, ASSO)).isFalse();
    }

    // --------------------------------------------------- appartenance et refus

    @Test
    @DisplayName("l'appartenance est une question distincte du droit d'agir")
    void appartenance() {
        // C'est le contrôle des routes de LECTURE du tableau de bord : un
        // responsable évènements n'a pas le droit de publier, il a le droit de
        // voir les brouillons de son mandat.
        siege(Poste.RESP_EVENEMENTS);
        assertThat(politique.estMembre(PERSONNE, MANDAT)).isTrue();
        assertThatCode(() -> politique.exigerMembre(PERSONNE, MANDAT)).doesNotThrowAnyException();

        when(membres.posteActif(MANDAT, PERSONNE)).thenReturn(Optional.empty());
        assertThat(politique.estMembre(PERSONNE, MANDAT)).isFalse();
        assertThatThrownBy(() -> politique.exigerMembre(PERSONNE, MANDAT))
                .isInstanceOf(Erreurs.AccesRefuse.class);
    }

    @Test
    @DisplayName("un refus lève, il ne rend pas false en silence")
    void exigerLeve() {
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat(StatutMandat.CLOS)));
        siege(Poste.PRESIDENT);

        assertThatThrownBy(() ->
                politique.exigerSurMandat(PERSONNE, Permission.PAGE_EDITER, MANDAT))
                .isInstanceOf(Erreurs.AccesRefuse.class);

        when(mandats.mandatEnFonction(ASSO)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> politique.exiger(PERSONNE, Permission.CAMPAGNE_GERER, ASSO))
                .isInstanceOf(Erreurs.AccesRefuse.class);
    }
}
