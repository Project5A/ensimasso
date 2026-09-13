package fr.ensim.asso;

import fr.ensim.asso.media.app.RejetMedia;
import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.media.domain.StatutMedia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * L'enregistrement d'un rejet, et surtout sa propagation transactionnelle.
 */
class RejetMediaTest {

    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID DEPOSANT = UUID.randomUUID();

    @Test
    @DisplayName("le rejet passe le média en REJETE et l'enregistre")
    void enregistreLeRejet() {
        var medias = mock(MediaAssetRepository.class);
        var media = new MediaAsset(ASSO, "2025-2026", "cle", "logo.png", "image/png", DEPOSANT);
        UUID id = UUID.randomUUID();
        when(medias.findById(id)).thenReturn(Optional.of(media));

        new RejetMedia(medias).enregistrer(id);

        assertThat(media.getStatut()).isEqualTo(StatutMedia.REJETE);
        verify(medias).save(media);
    }

    @Test
    @DisplayName("rejouer un rejet ne casse rien")
    void rejeuSansEffet() {
        var medias = mock(MediaAssetRepository.class);
        var media = new MediaAsset(ASSO, "2025-2026", "cle", "logo.png", "image/png", DEPOSANT);
        media.rejeter();
        UUID id = UUID.randomUUID();
        when(medias.findById(id)).thenReturn(Optional.of(media));

        new RejetMedia(medias).enregistrer(id);

        assertThat(media.getStatut()).isEqualTo(StatutMedia.REJETE);
        verify(medias, never()).save(any());
    }

    @Test
    @DisplayName("la transaction est bien REQUIRES_NEW, sans quoi le rejet serait annulé")
    void propagationSeparee() throws Exception {
        // Ce n'est pas un détail d'implémentation : toute la raison d'être de
        // cette classe est là. Dans la transaction appelante, l'exception qui
        // produit le 409 annulerait le passage en REJETE, pendant que la
        // suppression dans le stockage — qui n'a pas de rollback — tiendrait
        // bon. Il resterait une ligne ATTENTE_DEPOT désignant un objet disparu.
        Transactional annotation = RejetMedia.class
                .getMethod("enregistrer", UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation)
                .as("sans @Transactional, le rejet suit le sort de l'appelant")
                .isNotNull();
        assertThat(annotation.propagation())
                .as("REQUIRES_NEW : le rejet doit survivre à l'exception qui suit")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("RejetMedia est un composant à part : un appel interne ne traverserait pas le proxy")
    void composantDistinct() {
        // Une méthode privée annotée @Transactional dans ServiceMedia aurait
        // l'air correcte et n'aurait aucun effet : Spring n'intercepte que les
        // appels qui passent par le proxy. Le piège est silencieux, d'où la
        // classe séparée — et d'où ce cas, qui empêche de l'y replier un jour.
        assertThat(RejetMedia.class.getAnnotation(
                        org.springframework.stereotype.Component.class))
                .as("RejetMedia doit rester un bean Spring distinct")
                .isNotNull();
    }
}
