package fr.ensim.asso.gouvernance.api;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.security.Utilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gouvernance")
public class GouvernanceController {

    private final AssociationRepository associations;
    private final MandatRepository mandats;
    private final MembreBureauRepository membres;
    private final PolitiqueAcces politique;

    public GouvernanceController(AssociationRepository associations, MandatRepository mandats,
                                 MembreBureauRepository membres, PolitiqueAcces politique) {
        this.associations = associations;
        this.mandats = mandats;
        this.membres = membres;
        this.politique = politique;
    }

    /** Créer une association est une action de plateforme, pas d'association :
     *  c'est le seul endroit où un rôle global du jeton suffit. */
    @PostMapping("/associations")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssociationVue creer(@Valid @RequestBody CreerAssociation corps) {
        if (associations.existsBySlug(corps.slug())) {
            throw new IllegalStateException("le slug « " + corps.slug() + " » est déjà pris");
        }
        Association a = associations.save(new Association(
                corps.slug(), corps.nom(), Association.TypeAssociation.valueOf(corps.type())));
        return AssociationVue.de(a);
    }

    @GetMapping("/associations")
    public List<AssociationVue> lister() {
        return associations.findAll().stream().map(AssociationVue::de).toList();
    }

    @GetMapping("/associations/{slug}/mandats")
    public List<MandatVue> mandatsDe(@PathVariable String slug) {
        Association a = associations.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("association inconnue : " + slug));
        return mandats.findByAssociationIdOrderByDebutLeDesc(a.getId()).stream()
                .map(MandatVue::de).toList();
    }

    /**
     * Le bureau d'un mandat : la même donnée sert les droits et l'affichage.
     *
     * <p>Réservé à ce bureau-là. Cette route n'exerçait aucune autorisation :
     * elle rendait à tout compte authentifié le {@code personneId} — le sujet
     * Keycloak — de chaque membre, les membres explicitement marqués non
     * publics, et la composition d'un mandat en PREPARATION, c'est-à-dire le
     * bureau entrant avant son annonce en assemblée générale. Le portail
     * public, lui, filtre {@code visiblePublic} et ne rend jamais d'identifiant
     * de personne : le trombinoscope passe par là, pas par ici.
     */
    @GetMapping("/mandats/{mandatId}/bureau")
    public List<MembreVue> bureau(@PathVariable UUID mandatId) {
        politique.exigerMembre(Utilisateur.idCourantObligatoire(), mandatId);
        return membres.membresActifs(mandatId).stream().map(MembreVue::de).toList();
    }

    /** Les postes actifs de l'utilisateur courant : ce que le tableau de bord
     *  interroge au démarrage pour savoir quoi afficher. Jamais lu du jeton. */
    @GetMapping("/moi/postes")
    public List<PosteVue> mesPostes() {
        UUID moi = Utilisateur.idCourantObligatoire();
        return membres.postesActifsDe(moi).stream()
                .map(m -> {
                    Mandat mandat = mandats.findById(m.getMandatId()).orElseThrow();
                    return new PosteVue(mandat.getAssociationId(), mandat.getId(),
                            mandat.getAnneeCode(), m.getPoste().name());
                })
                .toList();
    }

    // ------------------------------------------------------------- corps

    public record CreerAssociation(
            @NotBlank @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") @Size(max = 60) String slug,
            @NotBlank @Size(max = 200) String nom,
            @NotBlank @Pattern(regexp = "BUREAU|CLUB|TECHNIQUE") String type) { }

    // -------------------------------------------------------------- vues

    public record AssociationVue(UUID id, String slug, String nom, String type) {
        static AssociationVue de(Association a) {
            return new AssociationVue(a.getId(), a.getSlug(), a.getNom(), a.getTypeAsso().name());
        }
    }

    public record MandatVue(UUID id, String anneeCode, String statut,
                            OffsetDateTime debutLe, OffsetDateTime finLe) {
        static MandatVue de(Mandat m) {
            return new MandatVue(m.getId(), m.getAnneeCode(), m.getStatut().name(),
                    m.getDebutLe(), m.getFinLe());
        }
    }

    public record MembreVue(UUID id, UUID personneId, String poste, String titreAffiche,
                            int ordre, String photoMediaKey) {
        static MembreVue de(MembreBureau m) {
            return new MembreVue(m.getId(), m.getPersonneId(), m.getPoste().name(),
                    m.getTitreAffiche(), m.getOrdre(), m.getPhotoMediaKey());
        }
    }

    public record PosteVue(UUID associationId, UUID mandatId, String anneeCode, String poste) { }
}
