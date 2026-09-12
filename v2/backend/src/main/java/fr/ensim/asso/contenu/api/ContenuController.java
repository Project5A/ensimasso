package fr.ensim.asso.contenu.api;

import com.fasterxml.jackson.databind.JsonNode;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API d'édition. Toutes les routes exigent une identité (refus par défaut dans
 * {@code SecurityConfig}) et l'autorisation fine est décidée par
 * {@code PolitiqueAcces} dans le service — jamais par un motif d'URL.
 */
@RestController
@RequestMapping("/api/contenu")
public class ContenuController {

    private final ServiceContenu service;
    private final Clock horloge;

    public ContenuController(ServiceContenu service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------- catalogue

    /** Le catalogue des blocs, avec leur JSON Schema : le formulaire d'édition
     *  du tableau de bord est généré à partir de là. Ajouter un type de bloc
     *  ne demande donc aucune modification du constructeur de pages. */
    @GetMapping("/types-blocs")
    public List<TypeBlocVue> catalogue() {
        return service.catalogueDesBlocs().stream()
                .map(t -> new TypeBlocVue(t.getType(), t.getSchemaVersion(), t.getLibelle(),
                        t.getCategorie(), t.getComposantReact(), t.getJsonSchema()))
                .toList();
    }

    // -------------------------------------------------------------- pages

    @PostMapping("/mandats/{mandatId}/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public PageVue creerPage(@PathVariable UUID mandatId, @Valid @RequestBody CreerPage corps) {
        Page p = service.creerPage(Utilisateur.idCourantObligatoire(), mandatId,
                corps.slug(), corps.titre(), corps.ordreMenu());
        return PageVue.de(p);
    }

    @GetMapping("/mandats/{mandatId}/pages")
    public List<PageVue> pages(@PathVariable UUID mandatId) {
        return service.pagesDuMandat(mandatId).stream().map(PageVue::de).toList();
    }

    @PostMapping("/pages/{pageId}/brouillon")
    public VersionVue ouvrirBrouillon(@PathVariable UUID pageId) {
        return VersionVue.de(service.ouvrirBrouillon(Utilisateur.idCourantObligatoire(), pageId));
    }

    // -------------------------------------------------------------- blocs

    @PostMapping("/versions/{versionId}/blocs")
    @ResponseStatus(HttpStatus.CREATED)
    public BlocVue ajouterBloc(@PathVariable UUID versionId, @Valid @RequestBody AjouterBloc corps) {
        Bloc b = service.ajouterBloc(Utilisateur.idCourantObligatoire(), versionId,
                corps.type(), corps.payload().toString(), corps.ordre());
        return BlocVue.de(b);
    }

    @GetMapping("/versions/{versionId}/blocs")
    public List<BlocVue> blocs(@PathVariable UUID versionId) {
        return service.blocsDe(versionId).stream().map(BlocVue::de).toList();
    }

    @PutMapping("/blocs/{blocId}")
    public BlocVue modifierBloc(@PathVariable UUID blocId, @Valid @RequestBody ModifierBloc corps) {
        return BlocVue.de(service.modifierBloc(
                Utilisateur.idCourantObligatoire(), blocId, corps.payload().toString()));
    }

    @DeleteMapping("/blocs/{blocId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerBloc(@PathVariable UUID blocId) {
        service.supprimerBloc(Utilisateur.idCourantObligatoire(), blocId);
    }

    @PutMapping("/versions/{versionId}/ordre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reordonner(@PathVariable UUID versionId, @Valid @RequestBody Reordonner corps) {
        service.reordonner(Utilisateur.idCourantObligatoire(), versionId, corps.blocs());
    }

    // -------------------------------------------------------- publication

    @PostMapping("/versions/{versionId}/publier")
    public VersionVue publier(@PathVariable UUID versionId) {
        return VersionVue.de(service.publier(Utilisateur.idCourantObligatoire(),
                versionId, OffsetDateTime.now(horloge)));
    }

    @PostMapping("/pages/{pageId}/restaurer/{versionId}")
    public ResponseEntity<VersionVue> restaurer(@PathVariable UUID pageId, @PathVariable UUID versionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(VersionVue.de(
                service.restaurer(Utilisateur.idCourantObligatoire(), pageId, versionId)));
    }

    // ------------------------------------------------------------- corps

    public record CreerPage(
            @NotBlank @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
                    message = "slug invalide (minuscules, chiffres et tirets)")
            @Size(max = 60) String slug,
            @NotBlank @Size(max = 200) String titre,
            @Min(0) @Max(100) int ordreMenu) { }

    public record AjouterBloc(
            @NotBlank @Size(max = 40) String type,
            @NotNull JsonNode payload,
            @Min(0) Integer ordre) { }

    public record ModifierBloc(@NotNull JsonNode payload) { }

    public record Reordonner(@NotEmpty List<UUID> blocs) { }

    // -------------------------------------------------------------- vues

    public record PageVue(UUID id, UUID mandatId, String slug, String titre, int ordreMenu) {
        static PageVue de(Page p) {
            return new PageVue(p.getId(), p.getMandatId(), p.getSlug(), p.getTitre(), p.getOrdreMenu());
        }
    }

    public record VersionVue(UUID id, UUID pageId, int numero, String statut,
                             OffsetDateTime publieLe, String note) {
        static VersionVue de(PageVersion v) {
            return new VersionVue(v.getId(), v.getPageId(), v.getNumero(),
                    v.getStatut().name(), v.getPublieLe(), v.getNote());
        }
    }

    public record BlocVue(UUID id, int ordre, String type, int schemaVersion,
                          String payload, boolean visible) {
        static BlocVue de(Bloc b) {
            return new BlocVue(b.getId(), b.getOrdre(), b.getType(),
                    b.getSchemaVersion(), b.getPayload(), b.isVisible());
        }
    }

    public record TypeBlocVue(String type, int schemaVersion, String libelle,
                              String categorie, String composantReact, String jsonSchema) { }
}
