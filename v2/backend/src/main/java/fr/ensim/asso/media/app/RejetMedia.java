package fr.ensim.asso.media.app;

import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Enregistre le rejet d'un dépôt dans une transaction À PART.
 *
 * <p>Un rejet doit à la fois laisser une trace en base ET faire échouer la
 * requête. Les deux sont incompatibles dans une seule transaction : l'exception
 * qui produit le 409 annule tout ce qui la précède, y compris le passage en
 * REJETE. C'est ce qui se passait — le statut revenait à ATTENTE_DEPOT pendant
 * que l'objet, lui, était bel et bien supprimé du stockage, qui n'a pas de
 * rollback. Restaient des lignes ATTENTE_DEPOT pointant vers des objets
 * inexistants, sans borne et sans explication.
 *
 * <p>{@code REQUIRES_NEW} suspend la transaction appelante et valide celle-ci
 * pour elle-même. Le rejet survit donc à l'exception qui suit. C'est un
 * composant distinct et non une méthode privée parce qu'un appel interne ne
 * traverse pas le proxy de Spring : l'annotation y serait sans effet — un piège
 * silencieux, exactement le genre qu'on cherche à supprimer ici.
 */
@Component
public class RejetMedia {

    private final MediaAssetRepository medias;

    public RejetMedia(MediaAssetRepository medias) {
        this.medias = medias;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(UUID mediaId) {
        MediaAsset media = medias.findById(mediaId)
                .orElseThrow(() -> new Erreurs.Introuvable("média", mediaId));
        if (media.getStatut() == fr.ensim.asso.media.domain.StatutMedia.ATTENTE_DEPOT) {
            media.rejeter();
            medias.save(media);
        }
    }
}
