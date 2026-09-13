package fr.ensim.asso;

import fr.ensim.asso.agenda.app.ServiceAgenda;
import fr.ensim.asso.agenda.domain.EvenementRepository;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Mandat;
import fr.ensim.asso.gouvernance.domain.MandatRepository;
import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.media.domain.MediasPublicables;
import fr.ensim.asso.partenariat.app.ServicePartenariat;
import fr.ensim.asso.partenariat.domain.NiveauPartenaire;
import fr.ensim.asso.partenariat.domain.Partenaire;
import fr.ensim.asso.partenariat.domain.PartenaireRepository;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Le média d'une autre association, servi depuis sa propre page.
 *
 * <p>Le portail résout l'affiche d'un évènement et le logo d'un partenaire
 * comme le reste de la page : par {@code medias.urlsDe(Collection)}, la
 * surcharge SANS identité, qui ne vérifie aucun droit — et qui n'a pas à le
 * faire, une page publiée étant publique. Le contrôle appartient donc à
 * l'écriture.
 *
 * <p>Il n'y était pas. Les deux services ne vérifiaient de leur clé qu'une
 * chose : « ça ne ressemble pas à une URL ». Connaître la clé d'un média d'une
 * autre association suffisait pour la poser sur son propre évènement et la
 * faire servir, signée, depuis sa propre page. Le contrôle ajouté à la
 * publication d'une PAGE ne voyait rien de ces deux chemins : ils ne passent
 * pas par {@code media_usage}.
 */
class MediasDAutruiTest {

    private static final UUID NOTRE_ASSO = UUID.randomUUID();
    private static final UUID AUTRE_ASSO = UUID.randomUUID();
    private static final UUID MANDAT = UUID.randomUUID();
    private static final UUID DEMANDEUR = UUID.randomUUID();
    private static final String A_NOUS = "bde/2025-2026/affiche.jpg";
    private static final String A_EUX = "club-photo/2025-2026/prive.jpg";
    private static final OffsetDateTime DEBUT = OffsetDateTime.parse("2026-06-01T20:00:00Z");

    private MediaAssetRepository medias;
    private MandatRepository mandats;
    private EvenementRepository evenements;
    private PartenaireRepository partenaires;
    private ServiceAgenda agenda;
    private ServicePartenariat partenariat;

    @BeforeEach
    void avant() {
        medias = mock(MediaAssetRepository.class);
        mandats = mock(MandatRepository.class);
        evenements = mock(EvenementRepository.class);
        partenaires = mock(PartenaireRepository.class);

        agenda = new ServiceAgenda(evenements, mock(PolitiqueAcces.class), medias, mandats,
                Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC));
        partenariat = new ServicePartenariat(partenaires, mock(PolitiqueAcces.class),
                medias, mandats);

        Mandat notre = Mandat.enPreparation(NOTRE_ASSO, "2025-2026",
                DEBUT.minusYears(1), DEBUT.plusYears(1));
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(notre));
        when(medias.findByCleIn(anyCollection())).thenReturn(List.of(
                disponible(NOTRE_ASSO, A_NOUS), disponible(AUTRE_ASSO, A_EUX)));
        when(evenements.save(any())).thenAnswer(i -> i.getArgument(0));
        when(partenaires.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MediaAsset disponible(UUID asso, String cle) {
        MediaAsset m = new MediaAsset(asso, "2025-2026", cle, cle, "image/jpeg", DEMANDEUR);
        m.confirmer(2_048L, "image/jpeg", DEBUT.minusMonths(2));
        return m;
    }

    private ServiceAgenda.Description evenement(String mediaKey) {
        return new ServiceAgenda.Description("Gala", null, null, "Palais",
                DEBUT, null, mediaKey, null, false);
    }

    private ServicePartenariat.Description partenaire(String logo) {
        return new ServicePartenariat.Description("Sponsor", NiveauPartenaire.OR, logo,
                "https://sponsor.example", 0, true);
    }

    // ------------------------------------------------------------- agenda

    @Test
    @DisplayName("une affiche d'évènement doit appartenir à l'association du mandat")
    void afficheDUneAutreAssociation() {
        assertThatThrownBy(() -> agenda.creer(DEMANDEUR, MANDAT, "gala", evenement(A_EUX)))
                .isInstanceOf(Erreurs.RequeteInvalide.class)
                .hasMessageContaining("appartient à une autre association");
        verify(evenements, never()).save(any());
    }

    @Test
    @DisplayName("une affiche inconnue est refusée à l'écriture, pas découverte à l'affichage")
    void afficheInconnue() {
        assertThatThrownBy(() ->
                agenda.creer(DEMANDEUR, MANDAT, "gala", evenement("bde/2025-2026/fantome.jpg")))
                .isInstanceOf(Erreurs.RequeteInvalide.class)
                .hasMessageContaining("aucun média ne porte cette clé");
    }

    @Test
    @DisplayName("notre propre affiche passe")
    void afficheANous() {
        assertThatCode(() -> agenda.creer(DEMANDEUR, MANDAT, "gala", evenement(A_NOUS)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un évènement sans affiche n'interroge pas la médiathèque")
    void evenementSansAffiche() {
        assertThatCode(() -> agenda.creer(DEMANDEUR, MANDAT, "gala", evenement(null)))
                .doesNotThrowAnyException();
        verify(medias, never()).findByCleIn(anyCollection());
    }

    @Test
    @DisplayName("modifier un évènement passe par le même contrôle que le créer")
    void modificationControleeAussi() {
        fr.ensim.asso.agenda.domain.Evenement e =
                new fr.ensim.asso.agenda.domain.Evenement(MANDAT, "gala", "Gala", DEBUT);
        when(evenements.findById(any())).thenReturn(Optional.of(e));

        // Le contrôle à la création seule laisserait la porte grande ouverte :
        // créer sans affiche, puis en poser une par un PATCH.
        assertThatThrownBy(() -> agenda.modifier(DEMANDEUR, UUID.randomUUID(), evenement(A_EUX)))
                .isInstanceOf(Erreurs.RequeteInvalide.class)
                .hasMessageContaining("appartient à une autre association");
    }

    // -------------------------------------------------------- partenariat

    @Test
    @DisplayName("le logo d'un partenaire doit appartenir à l'association du mandat")
    void logoDUneAutreAssociation() {
        assertThatThrownBy(() -> partenariat.creer(DEMANDEUR, MANDAT, partenaire(A_EUX)))
                .isInstanceOf(Erreurs.RequeteInvalide.class)
                .hasMessageContaining("appartient à une autre association");
        verify(partenaires, never()).save(any());
    }

    @Test
    @DisplayName("notre propre logo passe")
    void logoANous() {
        assertThatCode(() -> partenariat.creer(DEMANDEUR, MANDAT, partenaire(A_NOUS)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("modifier un partenaire passe par le même contrôle")
    void modificationPartenaireControlee() {
        Partenaire p = new Partenaire(MANDAT, "Sponsor", NiveauPartenaire.OR);
        when(partenaires.findById(any())).thenReturn(Optional.of(p));

        assertThatThrownBy(() ->
                partenariat.modifier(DEMANDEUR, UUID.randomUUID(), partenaire(A_EUX)))
                .isInstanceOf(Erreurs.RequeteInvalide.class)
                .hasMessageContaining("appartient à une autre association");
    }

    // ------------------------------------------------------- la règle même

    @Test
    @DisplayName("une clé vide n'est pas une clé : elle est ignorée, pas refusée")
    void cleVideIgnoree() {
        // La refuser produisait « (aucun média ne porte cette clé) » sans rien
        // nommer devant, et rendait la page impubliable sans dire quoi
        // corriger — le défaut même que ce contrôle existe pour supprimer.
        assertThat(MediasPublicables.refus(medias, NOTRE_ASSO, java.util.Arrays.asList("", "   ", null)))
                .isEmpty();
        verify(medias, never()).findByCleIn(anyCollection());
    }

    @Test
    @DisplayName("toutes les clés fautives sont nommées d'un coup")
    void toutesLesFautes() {
        List<String> refus = MediasPublicables.refus(medias, NOTRE_ASSO,
                List.of(A_NOUS, A_EUX, "bde/2025-2026/fantome.jpg"));

        assertThat(refus).hasSize(2);
        assertThat(String.join(" ; ", refus))
                .contains(A_EUX)
                .contains("fantome.jpg")
                .doesNotContain(A_NOUS);
    }
}
