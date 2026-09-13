package fr.ensim.asso.contenu;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.app.ValidationBloc;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Mandat;
import fr.ensim.asso.gouvernance.domain.MandatRepository;
import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Ce qu'une page a le droit de servir, vérifié avant de la figer.
 *
 * <p>La clé étrangère de {@code media_usage} refusait déjà une clé inconnue —
 * au COMMIT, en PostgreSQL, et sans dire laquelle. Publier une page dont un
 * bloc porte une clé morte sortait donc en 500, avec un message de contrainte,
 * sur une page que son bureau ne pouvait pas réparer sans deviner.
 *
 * <p>Et cette clé étrangère est plus faible qu'elle n'en a l'air : un média
 * SUPPRIMÉ garde sa ligne — volontairement, pour que les archives restent
 * explicables. Elle acceptait donc une image effacée du stockage, sur une page
 * que la publication rend immuable : cassée une fois, cassée pour toujours.
 */
class PublicationMediasTest {

    private static final UUID AUTEUR = UUID.randomUUID();
    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID AUTRE_ASSO = UUID.randomUUID();
    private static final UUID MANDAT = UUID.randomUUID();
    private static final OffsetDateTime MAINTENANT =
            OffsetDateTime.parse("2026-03-01T12:00:00Z");

    private PageRepository pages;
    private PageVersionRepository versions;
    private BlocRepository blocs;
    private MediaUsageRepository mediaUsages;
    private MediaAssetRepository medias;
    private ServiceContenu service;

    private Page page;
    private PageVersion brouillon;
    private final List<MediaUsage> indexees = new ArrayList<>();

    @BeforeEach
    void avant() {
        pages = mock(PageRepository.class);
        versions = mock(PageVersionRepository.class);
        blocs = mock(BlocRepository.class);
        mediaUsages = mock(MediaUsageRepository.class);
        medias = mock(MediaAssetRepository.class);
        MandatRepository mandats = mock(MandatRepository.class);

        service = new ServiceContenu(pages, versions, blocs, mediaUsages, medias, mandats,
                mock(TypeBlocRepository.class), mock(ValidationBloc.class),
                mock(PolitiqueAcces.class), new ObjectMapper());

        page = new Page(MANDAT, "accueil", "Accueil", 0);
        brouillon = new PageVersion(UUID.randomUUID(), 3, AUTEUR);

        Mandat mandat = Mandat.enPreparation(ASSO, "2025-2026",
                MAINTENANT.minusMonths(6), MAINTENANT.plusMonths(6));

        when(versions.findById(any())).thenReturn(Optional.of(brouillon));
        when(pages.findById(any())).thenReturn(Optional.of(page));
        when(mandats.findById(MANDAT)).thenReturn(Optional.of(mandat));
        when(versions.versionPubliee(any())).thenReturn(Optional.empty());

        indexees.clear();
        when(mediaUsages.save(any())).thenAnswer(i -> {
            indexees.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    private void pageAvec(String... cles) {
        List<Bloc> contenu = new ArrayList<>();
        int ordre = 0;
        for (String cle : cles) {
            contenu.add(new Bloc(brouillon.getId(), ordre++, "HERO", 1,
                    "{\"titre\":\"T\",\"mediaKey\":\"" + cle + "\"}"));
        }
        when(blocs.findByPageVersionIdOrderByOrdreAsc(any())).thenReturn(contenu);
    }

    private MediaAsset media(UUID asso, String cle, boolean disponible) {
        MediaAsset m = new MediaAsset(asso, "2025-2026", cle, cle, "image/jpeg", AUTEUR);
        if (disponible) {
            m.confirmer(1_024L, "image/jpeg", MAINTENANT.minusMonths(1));
        }
        return m;
    }

    private void enBase(MediaAsset... assets) {
        when(medias.findByCleIn(anyCollection())).thenReturn(List.of(assets));
    }

    @Test
    @DisplayName("une page dont les médias sont en règle se publie, et les indexe")
    void publicationNormale() {
        pageAvec("bde/2025-2026/a.jpg", "bde/2025-2026/b.jpg");
        enBase(media(ASSO, "bde/2025-2026/a.jpg", true),
               media(ASSO, "bde/2025-2026/b.jpg", true));

        assertThatCode(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .doesNotThrowAnyException();

        assertThat(brouillon.getStatut()).isEqualTo(StatutVersion.PUBLIEE);
        assertThat(indexees).extracting(MediaUsage::getMediaKey)
                .containsExactly("bde/2025-2026/a.jpg", "bde/2025-2026/b.jpg");
    }

    @Test
    @DisplayName("une clé inconnue est nommée, en 409, et la page reste un brouillon")
    void cleInconnue() {
        pageAvec("bde/2025-2026/fantome.jpg");
        enBase();   // la base ne connaît rien

        assertThatThrownBy(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("bde/2025-2026/fantome.jpg")
                .hasMessageContaining("aucun média ne porte cette clé");

        // Rien n'a été figé : la clé étrangère, elle, ne rendait la main qu'au
        // commit, après que la version publiée précédente avait été archivée.
        assertThat(brouillon.getStatut()).isEqualTo(StatutVersion.BROUILLON);
        assertThat(indexees).isEmpty();
        verify(versions, never()).flush();
    }

    @Test
    @DisplayName("un média supprimé passe la clé étrangère : il ne passe plus ici")
    void mediaSupprime() {
        MediaAsset efface = media(ASSO, "bde/2025-2026/efface.jpg", true);
        efface.marquerSupprime();
        pageAvec("bde/2025-2026/efface.jpg");
        enBase(efface);

        // Sa ligne existe encore — volontairement, pour que les archives
        // restent explicables — donc la contrainte de base l'acceptait. La page
        // aurait été figée sur une image absente du stockage.
        assertThatThrownBy(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("SUPPRIME");
    }

    @Test
    @DisplayName("un dépôt non confirmé ne se publie pas non plus")
    void mediaEnAttente() {
        pageAvec("bde/2025-2026/pas-encore.jpg");
        enBase(media(ASSO, "bde/2025-2026/pas-encore.jpg", false));

        assertThatThrownBy(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("ATTENTE_DEPOT");
    }

    @Test
    @DisplayName("le média d'une AUTRE association ne se sert pas depuis cette page")
    void mediaDUneAutreAssociation() {
        pageAvec("club-photo/2025-2026/prive.jpg");
        enBase(media(AUTRE_ASSO, "club-photo/2025-2026/prive.jpg", true));

        // Ce que la clé étrangère ne pouvait pas voir : elle vérifie l'existence,
        // pas l'appartenance. Le portail, sur une page publiée, ne vérifie aucun
        // droit sur les clés — et n'a pas à le faire.
        assertThatThrownBy(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("appartient à une autre association");
    }

    @Test
    @DisplayName("toutes les clés fautives sont nommées d'un coup")
    void toutesLesFautesDUnCoup() {
        pageAvec("bde/2025-2026/ok.jpg", "bde/2025-2026/fantome.jpg",
                 "club-photo/2025-2026/prive.jpg");
        enBase(media(ASSO, "bde/2025-2026/ok.jpg", true),
               media(AUTRE_ASSO, "club-photo/2025-2026/prive.jpg", true));

        // Corriger une page à raison d'un aller-retour par image serait une
        // punition, pas un message d'erreur.
        assertThatThrownBy(() -> service.publier(AUTEUR, brouillon.getId(), MAINTENANT))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("fantome.jpg")
                .hasMessageContaining("prive.jpg");
    }

    @Test
    @DisplayName("une page sans image n'interroge pas la médiathèque")
    void pageSansImage() {
        when(blocs.findByPageVersionIdOrderByOrdreAsc(any())).thenReturn(List.of(
                new Bloc(brouillon.getId(), 0, "TEXT", 1, "{\"corps\":\"Bonjour\"}")));

        service.publier(AUTEUR, brouillon.getId(), MAINTENANT);

        assertThat(brouillon.getStatut()).isEqualTo(StatutVersion.PUBLIEE);
        verify(medias, never()).findByCleIn(anyCollection());
    }
}
