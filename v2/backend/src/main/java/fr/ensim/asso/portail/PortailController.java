package fr.ensim.asso.portail;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Le seul chemin de lecture public. Aucune écriture, aucun jeton requis.
 *
 * <p>Les réponses portent un {@code Cache-Control} court : c'est le point où
 * brancher le cache Valkey décrit dans l'architecture — la clé contiendra
 * l'identifiant de version publiée, donc publier écrit une NOUVELLE clé et
 * l'invalidation n'existe pas comme problème.
 */
@RestController
@RequestMapping("/api/public")
public class PortailController {

    private final ServicePortail service;

    public PortailController(ServicePortail service) {
        this.service = service;
    }

    @GetMapping("/associations")
    public ResponseEntity<List<PageRendue.AssociationVue>> annuaire() {
        return avecCache(service.annuaire());
    }

    /** La page en cours d'une association. */
    @GetMapping("/associations/{slug}/pages/{slugPage}")
    public ResponseEntity<PageRendue> page(@PathVariable String slug, @PathVariable String slugPage) {
        return avecCache(service.page(slug, slugPage));
    }

    /** Raccourci : l'accueil. */
    @GetMapping("/associations/{slug}")
    public ResponseEntity<PageRendue> accueil(@PathVariable String slug) {
        return avecCache(service.page(slug, "accueil"));
    }

    /**
     * L'archive. Aucun code particulier : une autre année est un autre mandat.
     * Cache plus long — une page d'archive ne change plus jamais.
     */
    @GetMapping("/associations/{slug}/annees/{anneeCode}/pages/{slugPage}")
    public ResponseEntity<PageRendue> archive(@PathVariable String slug,
                                              @PathVariable String anneeCode,
                                              @PathVariable String slugPage) {
        PageRendue rendue = service.pageArchivee(slug, anneeCode, slugPage);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(bornee(Duration.ofHours(1))).cachePublic())
                .body(rendue);
    }

    @GetMapping("/associations/{slug}/annees/{anneeCode}")
    public ResponseEntity<PageRendue> archiveAccueil(@PathVariable String slug,
                                                     @PathVariable String anneeCode) {
        return archive(slug, anneeCode, "accueil");
    }

    private <T> ResponseEntity<T> avecCache(T corps) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(bornee(Duration.ofMinutes(5))).cachePublic())
                .body(corps);
    }

    /**
     * Aucune réponse n'est mémorisable plus longtemps que les URL signées
     * qu'elle transporte.
     *
     * <p>La même règle vaut pour le cache serveur et pour celui du navigateur :
     * une page gardée au-delà de la validité de ses URL de médias affiche des
     * images mortes. Le service plafonnait son propre cache ; le
     * {@code Cache-Control} y échappait, et l'archive était annoncée
     * mémorisable une heure alors que ses images meurent en trente minutes.
     */
    private Duration bornee(Duration souhaitee) {
        Duration plafond = service.dureeCachePublic();
        return souhaitee.compareTo(plafond) > 0 ? plafond : souhaitee;
    }
}
