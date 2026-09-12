package fr.ensim.asso.agenda.api;

import fr.ensim.asso.agenda.app.ServiceAgenda;
import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * L'agenda côté bureau. Aucune route n'est publique : la lecture publique passe
 * par {@code /api/public}, qui ne sert que les évènements non-brouillons du
 * mandat de la page.
 */
@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private final ServiceAgenda service;

    public AgendaController(ServiceAgenda service) {
        this.service = service;
    }

    @GetMapping("/mandats/{mandatId}/evenements")
    public List<EvenementVue> duMandat(@PathVariable UUID mandatId) {
        return service.tousDuMandat(Utilisateur.idCourantObligatoire(), mandatId)
                .stream().map(EvenementVue::de).toList();
    }

    @PostMapping("/mandats/{mandatId}/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    public EvenementVue creer(@PathVariable UUID mandatId, @Valid @RequestBody Redaction corps) {
        return EvenementVue.de(service.creer(
                Utilisateur.idCourantObligatoire(), mandatId, corps.slug(), corps.versDescription()));
    }

    @PutMapping("/evenements/{evenementId}")
    public EvenementVue modifier(@PathVariable UUID evenementId, @Valid @RequestBody Redaction corps) {
        return EvenementVue.de(service.modifier(
                Utilisateur.idCourantObligatoire(), evenementId, corps.versDescription()));
    }

    @PostMapping("/evenements/{evenementId}/publier")
    public EvenementVue publier(@PathVariable UUID evenementId) {
        return EvenementVue.de(service.publier(Utilisateur.idCourantObligatoire(), evenementId));
    }

    @PostMapping("/evenements/{evenementId}/annuler")
    public EvenementVue annuler(@PathVariable UUID evenementId, @RequestBody Annulation corps) {
        return EvenementVue.de(service.annuler(
                Utilisateur.idCourantObligatoire(), evenementId, corps.motif()));
    }

    @DeleteMapping("/evenements/{evenementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID evenementId) {
        service.supprimer(Utilisateur.idCourantObligatoire(), evenementId);
    }

    // ---------------------------------------------------------------- corps

    public record Redaction(
            String slug,
            @NotNull @Size(min = 1, max = 160) String titre,
            @Size(max = 400) String resume,
            String description,
            @Size(max = 200) String lieu,
            @NotNull OffsetDateTime debutLe,
            OffsetDateTime finLe,
            @Size(max = 512) String mediaKey,
            @Size(max = 512) String lien,
            boolean complet) {

        ServiceAgenda.Description versDescription() {
            return new ServiceAgenda.Description(titre, resume, description, lieu,
                    debutLe, finLe, mediaKey, lien, complet);
        }
    }

    public record Annulation(@Size(max = 400) String motif) { }

    /** Vue du tableau de bord : elle montre les brouillons, contrairement au portail. */
    public record EvenementVue(UUID id, String slug, String titre, String resume,
                               String description, String lieu,
                               OffsetDateTime debutLe, OffsetDateTime finLe,
                               String mediaKey, String lien, String statut,
                               boolean complet, String motifAnnulation) {

        static EvenementVue de(Evenement e) {
            return new EvenementVue(e.getId(), e.getSlug(), e.getTitre(), e.getResume(),
                    e.getDescription(), e.getLieu(), e.getDebutLe(), e.getFinLe(),
                    e.getMediaKey(), e.getLien(), e.getStatut().name(),
                    e.isComplet(), e.getMotifAnnulation());
        }
    }
}
