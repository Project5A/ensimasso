package fr.ensim.asso.adhesion.api;

import fr.ensim.asso.adhesion.app.ServiceAdhesion;
import fr.ensim.asso.adhesion.domain.*;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API des adhésions.
 *
 * <p>Noter ce qui <em>n'est pas</em> dans les corps de requête : aucun montant.
 * Le client choisit un public cible, le serveur lit le prix. C'est la
 * différence structurelle avec la v1, où {@code POST /api/payment/create-payment-intent}
 * acceptait {@code amount} depuis le navigateur.
 */
@RestController
@RequestMapping("/api/adhesions")
public class AdhesionController {

    private final ServiceAdhesion service;

    public AdhesionController(ServiceAdhesion service) {
        this.service = service;
    }

    // ----------------------------------------------------------- campagnes

    @PostMapping("/campagnes")
    @ResponseStatus(HttpStatus.CREATED)
    public CampagneVue ouvrirCampagne(@Valid @RequestBody OuvrirCampagne corps) {
        return CampagneVue.de(service.ouvrirCampagne(
                Utilisateur.idCourantObligatoire(), corps.associationId(),
                corps.couvreAnneeCode(), corps.fermeLe()));
    }

    @PostMapping("/campagnes/{campagneId}/tarifs")
    @ResponseStatus(HttpStatus.CREATED)
    public TarifVue definirTarif(@PathVariable UUID campagneId, @Valid @RequestBody DefinirTarif corps) {
        return TarifVue.de(service.definirTarif(
                Utilisateur.idCourantObligatoire(), campagneId, corps.libelle(),
                corps.montantCents(), PublicCible.valueOf(corps.publicCible())));
    }

    @GetMapping("/campagnes/{campagneId}/tarifs")
    public List<TarifVue> tarifs(@PathVariable UUID campagneId) {
        return service.tarifsDe(campagneId).stream().map(TarifVue::de).toList();
    }

    @DeleteMapping("/campagnes/{campagneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fermerCampagne(@PathVariable UUID campagneId) {
        service.fermerCampagne(Utilisateur.idCourantObligatoire(), campagneId);
    }

    // ----------------------------------------------------------- adhésions

    /** Adhérer. Le corps ne contient volontairement aucun montant. */
    @PostMapping("/campagnes/{campagneId}/adherer")
    @ResponseStatus(HttpStatus.CREATED)
    public AdhesionVue adherer(@PathVariable UUID campagneId, @Valid @RequestBody Adherer corps) {
        return AdhesionVue.de(service.adherer(
                Utilisateur.idCourantObligatoire(), Utilisateur.rolesGlobaux(), campagneId,
                PublicCible.valueOf(corps.publicCible())));
    }

    @GetMapping("/moi")
    public List<AdhesionVue> mesAdhesions() {
        return service.mesAdhesions(Utilisateur.idCourantObligatoire())
                .stream().map(AdhesionVue::de).toList();
    }

    @GetMapping("/moi/associations/{associationId}")
    public EstAdherentVue suisJeAdherent(@PathVariable UUID associationId) {
        return new EstAdherentVue(
                service.estAdherent(Utilisateur.idCourantObligatoire(), associationId),
                service.anneeCouranteCode().orElse(null));
    }

    /** Liste nominative : réservée au bureau, jamais publique. */
    @GetMapping("/associations/{associationId}/annees/{anneeCode}")
    public List<AdhesionVue> adherents(@PathVariable UUID associationId, @PathVariable String anneeCode) {
        return service.adherentsDe(Utilisateur.idCourantObligatoire(), associationId, anneeCode)
                .stream().map(AdhesionVue::de).toList();
    }

    // -------------------------------------------------------------- corps

    public record OuvrirCampagne(
            @NotNull UUID associationId,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "format attendu : 2026-2027")
            String couvreAnneeCode,
            OffsetDateTime fermeLe) { }

    public record DefinirTarif(
            @NotBlank @Size(max = 120) String libelle,
            @Min(0) @Max(100_000) int montantCents,
            @NotBlank @Pattern(regexp = "ETUDIANT|EXTERIEUR|ANCIEN") String publicCible) { }

    /** Pas de montant ici, délibérément : le serveur le détermine. */
    public record Adherer(
            @NotBlank @Pattern(regexp = "ETUDIANT|EXTERIEUR|ANCIEN") String publicCible) { }

    // --------------------------------------------------------------- vues

    public record CampagneVue(UUID id, UUID associationId, String couvreAnneeCode,
                              String statut, OffsetDateTime ouvreLe, OffsetDateTime fermeLe) {
        static CampagneVue de(CampagneAdhesion c) {
            return new CampagneVue(c.getId(), c.getAssociationId(), c.getCouvreAnneeCode(),
                    c.getStatut().name(), c.getOuvreLe(), c.getFermeLe());
        }
    }

    public record TarifVue(UUID id, String libelle, int montantCents, String publicCible) {
        static TarifVue de(TarifAdhesion t) {
            return new TarifVue(t.getId(), t.getLibelle(), t.getMontantCents(),
                    t.getPublicCible().name());
        }
    }

    public record AdhesionVue(UUID id, UUID associationId, String couvreAnneeCode,
                              int montantPayeCents, String statut, OffsetDateTime activeeLe) {
        static AdhesionVue de(Adhesion a) {
            return new AdhesionVue(a.getId(), a.getAssociationId(), a.getCouvreAnneeCode(),
                    a.getMontantPayeCents(), a.getStatut().name(), a.getActiveeLe());
        }
    }

    public record EstAdherentVue(boolean adherent, String anneeCourante) { }
}
