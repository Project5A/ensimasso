package fr.ensim.asso.partenariat.api;

import fr.ensim.asso.partenariat.app.ServicePartenariat;
import fr.ensim.asso.partenariat.domain.NiveauPartenaire;
import fr.ensim.asso.partenariat.domain.Partenaire;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Les partenaires côté bureau. */
@RestController
@RequestMapping("/api/partenaires")
public class PartenariatController {

    private final ServicePartenariat service;

    public PartenariatController(ServicePartenariat service) {
        this.service = service;
    }

    @GetMapping("/mandats/{mandatId}")
    public List<PartenaireVue> duMandat(@PathVariable UUID mandatId) {
        return service.tousDuMandat(Utilisateur.idCourantObligatoire(), mandatId)
                .stream().map(PartenaireVue::de).toList();
    }

    @PostMapping("/mandats/{mandatId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PartenaireVue creer(@PathVariable UUID mandatId, @Valid @RequestBody Redaction corps) {
        return PartenaireVue.de(service.creer(
                Utilisateur.idCourantObligatoire(), mandatId, corps.versDescription()));
    }

    @PutMapping("/{partenaireId}")
    public PartenaireVue modifier(@PathVariable UUID partenaireId, @Valid @RequestBody Redaction corps) {
        return PartenaireVue.de(service.modifier(
                Utilisateur.idCourantObligatoire(), partenaireId, corps.versDescription()));
    }

    @DeleteMapping("/{partenaireId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID partenaireId) {
        service.supprimer(Utilisateur.idCourantObligatoire(), partenaireId);
    }

    public record Redaction(
            @NotNull @Size(min = 1, max = 120) String nom,
            @NotNull NiveauPartenaire niveau,
            @Size(max = 512) String logoMediaKey,
            @Size(max = 512) String url,
            int ordre,
            boolean visible) {

        ServicePartenariat.Description versDescription() {
            return new ServicePartenariat.Description(nom, niveau, logoMediaKey, url, ordre, visible);
        }
    }

    public record PartenaireVue(UUID id, String nom, String niveau, String logoMediaKey,
                                String url, int ordre, boolean visible) {

        static PartenaireVue de(Partenaire p) {
            return new PartenaireVue(p.getId(), p.getNom(), p.getNiveau().name(),
                    p.getLogoMediaKey(), p.getUrl(), p.getOrdre(), p.isVisible());
        }
    }
}
