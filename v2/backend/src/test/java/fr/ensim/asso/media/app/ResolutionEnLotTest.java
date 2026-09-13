package fr.ensim.asso.media.app;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.AnneeUniversitaireRepository;
import fr.ensim.asso.gouvernance.domain.AssociationRepository;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.media.domain.PortStockage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * La résolution d'URL en lot.
 *
 * <p>« Résolution en lot : une page de galerie ne doit pas faire N requêtes »,
 * disait le commentaire de {@code urlsDe}. La méthode bouclait pourtant sur
 * {@code findByCle} : une requête par clé, exactement ce que la phrase
 * prétendait éviter. Un commentaire qui décrit le contraire du code est pire
 * que pas de commentaire — il dispense de regarder.
 *
 * <p>Ces cas comptent donc les requêtes, et pas seulement le résultat. Le
 * résultat était juste ; c'est son coût qui ne l'était pas, et un test qui ne
 * regarde que le résultat laisserait la boucle revenir sans rien dire.
 */
class ResolutionEnLotTest {

    private static final UUID ASSO_VISIBLE = UUID.randomUUID();
    private static final UUID ASSO_INTERDITE = UUID.randomUUID();
    private static final UUID DEMANDEUR = UUID.randomUUID();

    private MediaAssetRepository medias;
    private PortStockage stockage;
    private PolitiqueAcces politique;
    private ServiceMedia service;

    @BeforeEach
    void avant() {
        medias = mock(MediaAssetRepository.class);
        stockage = mock(PortStockage.class);
        politique = mock(PolitiqueAcces.class);
        service = new ServiceMedia(medias, stockage,
                mock(AssociationRepository.class), mock(AnneeUniversitaireRepository.class),
                politique, mock(RejetMedia.class), new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));

        when(stockage.urlLecture(anyString(), any(Duration.class)))
                .thenAnswer(i -> "https://stockage.invalide/" + i.getArgument(0) + "?sig=x");
    }

    /** Un média confirmé, donc servable. */
    private MediaAsset disponible(UUID asso, String cle) {
        MediaAsset m = new MediaAsset(asso, "2025-2026", cle, cle, "image/jpeg", DEMANDEUR);
        m.confirmer(1_024L, "image/jpeg", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return m;
    }

    /** Un média dont la clé est réservée mais dont l'objet n'est pas confirmé. */
    private MediaAsset enAttente(String cle) {
        return new MediaAsset(ASSO_VISIBLE, "2025-2026", cle, cle, "image/jpeg", DEMANDEUR);
    }

    @Test
    @DisplayName("dix clés coûtent UNE requête, pas dix")
    void uneSeuleRequete() {
        List<String> cles = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        when(medias.findByCleIn(anyCollection())).thenReturn(
                cles.stream().map(c -> disponible(ASSO_VISIBLE, c)).toList());

        Map<String, String> urls = service.urlsDe(cles);

        assertThat(urls).hasSize(10);
        verify(medias, times(1)).findByCleIn(anyCollection());
        // C'est LA vérification. Sans elle, la boucle d'origine repasserait
        // tous les cas de ce fichier au vert.
        verify(medias, never()).findByCle(anyString());
    }

    @Test
    @DisplayName("l'ordre demandé est conservé, quel que soit celui de la base")
    void ordreConserve() {
        // La base ne promet aucun ordre sur un « in » ; le portail, lui, écrit
        // ces URL dans une page. Elles doivent sortir dans l'ordre demandé.
        when(medias.findByCleIn(anyCollection())).thenReturn(List.of(
                disponible(ASSO_VISIBLE, "troisieme"),
                disponible(ASSO_VISIBLE, "premiere"),
                disponible(ASSO_VISIBLE, "deuxieme")));

        assertThat(service.urlsDe(List.of("premiere", "deuxieme", "troisieme")))
                .containsExactly(
                        Map.entry("premiere", "https://stockage.invalide/premiere?sig=x"),
                        Map.entry("deuxieme", "https://stockage.invalide/deuxieme?sig=x"),
                        Map.entry("troisieme", "https://stockage.invalide/troisieme?sig=x"));
    }

    @Test
    @DisplayName("une clé inconnue ou non confirmée est absente, pas nulle")
    void clesNonServables() {
        when(medias.findByCleIn(anyCollection()))
                .thenReturn(List.of(disponible(ASSO_VISIBLE, "prete"), enAttente("pas-encore")));

        Map<String, String> urls = service.urlsDe(List.of("prete", "pas-encore", "jamais-vue"));

        assertThat(urls).containsOnlyKeys("prete");
        // Signer une URL pour un objet qui n'est pas là servirait un 404 à la
        // place d'une image : mieux vaut que la page n'en parle pas.
        verify(stockage, never()).urlLecture(eq("pas-encore"), any());
        verify(stockage, never()).urlLecture(eq("jamais-vue"), any());
    }

    @Test
    @DisplayName("aucune clé, aucune requête")
    void aucuneCle() {
        assertThat(service.urlsDe(List.of())).isEmpty();
        verifyNoInteractions(medias);
    }

    @Test
    @DisplayName("les doublons ne se paient qu'une fois")
    void doublons() {
        when(medias.findByCleIn(anyCollection()))
                .thenReturn(List.of(disponible(ASSO_VISIBLE, "logo")));

        // Un même logo sur trois blocs de la même page : c'est le cas courant.
        assertThat(service.urlsDe(List.of("logo", "logo", "logo"))).containsOnlyKeys("logo");
        verify(stockage, times(1)).urlLecture(eq("logo"), any());
    }

    @Test
    @DisplayName("la résolution avec identité filtre toujours, et en une requête")
    void filtrageParPermission() {
        when(medias.findByCleIn(anyCollection())).thenReturn(List.of(
                disponible(ASSO_VISIBLE, "la-mienne"),
                disponible(ASSO_INTERDITE, "celle-d-une-autre")));
        when(politique.peut(DEMANDEUR, Permission.MEDIA_DEPOSER, ASSO_VISIBLE)).thenReturn(true);
        when(politique.peut(DEMANDEUR, Permission.MEDIA_DEPOSER, ASSO_INTERDITE)).thenReturn(false);

        Map<String, String> urls =
                service.urlsDe(DEMANDEUR, List.of("la-mienne", "celle-d-une-autre"));

        // Une clé refusée est ABSENTE, comme une clé inconnue : distinguer
        // « interdit » de « inexistant » renseignerait sur ce qu'on n'a pas le
        // droit de voir.
        assertThat(urls).containsOnlyKeys("la-mienne");
        verify(medias, times(1)).findByCleIn(anyCollection());
        verify(medias, never()).findByCle(anyString());
    }

    @Test
    @DisplayName("la requête ne demande que les clés distinctes")
    void requeteDedoublonnee() {
        when(medias.findByCleIn(anyCollection()))
                .thenReturn(List.of(disponible(ASSO_VISIBLE, "x")));

        service.urlsDe(List.of("x", "x", "y", "x"));

        org.mockito.ArgumentCaptor<Collection<String>> capture =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(medias).findByCleIn(capture.capture());
        assertThat(capture.getValue()).containsExactly("x", "y");
    }
}
