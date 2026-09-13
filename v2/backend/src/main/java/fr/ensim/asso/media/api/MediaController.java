package fr.ensim.asso.media.api;

import fr.ensim.asso.media.app.ServiceMedia;
import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API de la médiathèque.
 *
 * <p>Le dépôt se fait en deux temps — demander une URL, puis confirmer — parce
 * que les octets vont directement du navigateur au stockage. L'application ne
 * les voit jamais : c'est ce qui rend possible l'isolement du traitement des
 * fichiers hostiles, et c'est l'inverse de la v1 où
 * {@code POST /api/posts/uploadImage} recevait 20 Mo sans authentification.
 */
@RestController
@RequestMapping("/api/medias")
public class MediaController {

    private final ServiceMedia service;

    public MediaController(ServiceMedia service) {
        this.service = service;
    }

    /** 1/2 — réserver une clé et obtenir l'URL de dépôt direct. */
    @PostMapping("/depots")
    @ResponseStatus(HttpStatus.CREATED)
    public DepotVue preparerDepot(@Valid @RequestBody PreparerDepot corps) {
        var d = service.preparerDepot(Utilisateur.idCourantObligatoire(),
                corps.associationId(), corps.nomOriginal(), corps.contentType());
        return new DepotVue(d.mediaId(), d.cle(), d.url().url(), d.url().methode(),
                d.url().enTetes(), d.url().expireLe(), d.tailleMaxOctets());
    }

    /** 2/2 — confirmer : la taille et le type réels sont relus dans le stockage. */
    @PostMapping("/{mediaId}/confirmer")
    public MediaVue confirmer(@PathVariable UUID mediaId) {
        return MediaVue.de(service.confirmerDepot(Utilisateur.idCourantObligatoire(), mediaId));
    }

    @GetMapping("/associations/{associationId}/annees/{anneeCode}")
    public List<MediaVue> mediatheque(@PathVariable UUID associationId, @PathVariable String anneeCode) {
        return service.mediatheque(Utilisateur.idCourantObligatoire(), associationId, anneeCode)
                .stream().map(MediaVue::de).toList();
    }

    /**
     * Résout des clés en URL de lecture, fabriquées à l'instant.
     *
     * <p>En lot, parce qu'une galerie ne doit pas déclencher N requêtes. Et
     * jamais persistées côté client au-delà de leur durée de vie : la clé est
     * la référence stable, l'URL ne l'est pas.
     */
    @PostMapping("/urls")
    public Map<String, String> urls(@Valid @RequestBody ResoudreUrls corps) {
        return service.urlsDe(Utilisateur.idCourantObligatoire(), corps.cles());
    }

    @PutMapping("/{mediaId}/description")
    public MediaVue decrire(@PathVariable UUID mediaId, @Valid @RequestBody Decrire corps) {
        return MediaVue.de(service.decrire(
                Utilisateur.idCourantObligatoire(), mediaId, corps.texteAlternatif()));
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID mediaId) {
        service.supprimer(Utilisateur.idCourantObligatoire(), mediaId);
    }

    // -------------------------------------------------------------- corps

    public record PreparerDepot(
            @NotNull UUID associationId,
            @Size(max = 255) String nomOriginal,
            @NotBlank @Size(max = 100) String contentType) { }

    public record ResoudreUrls(@NotEmpty @Size(max = 100) List<@NotBlank String> cles) { }

    public record Decrire(@NotBlank @Size(max = 500) String texteAlternatif) { }

    // --------------------------------------------------------------- vues

    public record DepotVue(UUID mediaId, String cle, String url, String methode,
                           Map<String, String> enTetes, OffsetDateTime expireLe,
                           long tailleMaxOctets) { }

    public record MediaVue(UUID id, String cle, String contentType, Long tailleOctets,
                           Integer largeur, Integer hauteur, String blurhash,
                           String texteAlternatif, String statut) {
        static MediaVue de(MediaAsset m) {
            return new MediaVue(m.getId(), m.getCle(), m.getContentType(), m.getTailleOctets(),
                    m.getLargeur(), m.getHauteur(), m.getBlurhash(),
                    m.getTexteAlternatif(), m.getStatut().name());
        }
    }
}
