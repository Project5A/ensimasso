package fr.ensim.asso.passation;

import fr.ensim.asso.gouvernance.domain.Poste;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Les quatre étapes de la passation. */
@RestController
@RequestMapping("/api/passations")
public class PassationController {

    private final ServicePassation service;
    private final Clock horloge;

    public PassationController(ServicePassation service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Vue preparer(@Valid @RequestBody Preparer corps) {
        var p = service.preparer(Utilisateur.idCourantObligatoire(), corps.associationId(),
                corps.debutPrevu(), corps.finPrevue());
        return Vue.de(p);
    }

    @PostMapping("/{id}/membres")
    @ResponseStatus(HttpStatus.CREATED)
    public void designer(@PathVariable UUID id, @Valid @RequestBody Designer corps) {
        service.designer(Utilisateur.idCourantObligatoire(), id,
                corps.personneId(), Poste.valueOf(corps.poste()), corps.ordre());
    }

    @PostMapping("/{id}/bureau-complet")
    public Vue bureauComplet(@PathVariable UUID id) {
        return Vue.de(service.marquerBureauComplete(Utilisateur.idCourantObligatoire(), id));
    }

    /** L'investiture, à l'AG. Le sortant est clos et l'entrant investi dans la
     *  même transaction ; la contrainte d'exclusion refuse tout chevauchement. */
    @PostMapping("/{id}/activer")
    public Vue activer(@PathVariable UUID id, @RequestBody(required = false) Activer corps) {
        OffsetDateTime quand = (corps != null && corps.aLAg() != null)
                ? corps.aLAg() : OffsetDateTime.now(horloge);
        return Vue.de(service.activer(Utilisateur.idCourantObligatoire(), id, quand));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void annuler(@PathVariable UUID id) {
        service.annuler(Utilisateur.idCourantObligatoire(), id);
    }

    public record Preparer(@NotNull UUID associationId,
                           @NotNull OffsetDateTime debutPrevu,
                           OffsetDateTime finPrevue) { }

    public record Designer(@NotNull UUID personneId,
                           @NotBlank @Pattern(regexp = "PRESIDENT|VICE_PRESIDENT|TRESORIER|SECRETAIRE|RESP_COM|RESP_EVENEMENTS|MEMBRE_BUREAU")
                           String poste,
                           @Min(0) int ordre) { }

    public record Activer(OffsetDateTime aLAg) { }

    public record Vue(UUID id, UUID associationId, UUID mandatSortantId, UUID mandatEntrantId,
                      String statut, int pagesClonees) {
        static Vue de(fr.ensim.asso.gouvernance.domain.Passation p) {
            return new Vue(p.getId(), p.getAssociationId(), p.getMandatSortantId(),
                    p.getMandatEntrantId(), p.getStatut().name(), p.getPagesClonees());
        }
    }
}
