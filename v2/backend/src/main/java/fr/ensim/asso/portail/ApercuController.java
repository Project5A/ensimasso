package fr.ensim.asso.portail;

import fr.ensim.asso.shared.security.Utilisateur;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * L'aperçu d'un brouillon, pour le bureau qui l'écrit.
 *
 * <p>Route volontairement distincte de {@code /api/public} : ce qui est servi
 * ici n'est pas publié. Le préfixe suffit à ce que la règle de sécurité par
 * défaut — tout exige une identité, sauf {@code /api/public} et le webhook —
 * s'applique sans exception à écrire.
 *
 * <p>{@code no-store} plutôt qu'un cache court : un brouillon change à chaque
 * enregistrement, et une version intermédiaire retenue dans un cache partagé
 * serait exactement ce qu'on cherche à éviter.
 */
@RestController
@RequestMapping("/api/apercu")
public class ApercuController {

    private final ServicePortail service;

    public ApercuController(ServicePortail service) {
        this.service = service;
    }

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<PageRendue> apercu(@PathVariable UUID versionId) {
        PageRendue rendue = service.apercu(Utilisateur.idCourantObligatoire(), versionId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(rendue);
    }
}
