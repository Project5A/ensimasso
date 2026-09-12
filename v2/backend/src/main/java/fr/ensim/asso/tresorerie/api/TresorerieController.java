package fr.ensim.asso.tresorerie.api;

import fr.ensim.asso.adhesion.domain.PublicCible;
import fr.ensim.asso.shared.security.Utilisateur;
import fr.ensim.asso.tresorerie.app.ServiceTresorerie;
import fr.ensim.asso.tresorerie.domain.Commande;
import fr.ensim.asso.tresorerie.domain.EcritureLedger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API de trésorerie.
 *
 * <p>Aucun corps de requête ne porte de montant. Comparer avec la v1 :
 * {@code POST /api/payment/create-payment-intent} acceptait
 * {@code {"amount": …, "currency": …}} depuis le navigateur, et
 * {@code POST /api/payment/confirm?eventId=&guestId=} inscrivait un participant
 * sans jamais contacter Stripe — les deux en {@code permitAll}.
 */
@RestController
@RequestMapping("/api/tresorerie")
public class TresorerieController {

    private final ServiceTresorerie service;

    public TresorerieController(ServiceTresorerie service) {
        this.service = service;
    }

    /** Commander une adhésion. Le prix vient des tarifs, pas du client. */
    @PostMapping("/commandes/adhesion")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandeVue commanderAdhesion(@Valid @RequestBody CommanderAdhesion corps) {
        return CommandeVue.de(service.commanderAdhesion(
                Utilisateur.idCourantObligatoire(), corps.campagneId(),
                PublicCible.valueOf(corps.publicCible())));
    }

    /** Le secret client, pour que Stripe.js finalise le paiement côté navigateur. */
    @GetMapping("/commandes/{commandeId}/secret")
    public SecretVue secret(@PathVariable UUID commandeId) {
        return new SecretVue(service.secretClientDe(Utilisateur.idCourantObligatoire(), commandeId));
    }

    @GetMapping("/commandes/moi")
    public List<CommandeVue> mesCommandes() {
        return service.mesCommandes(Utilisateur.idCourantObligatoire())
                .stream().map(CommandeVue::de).toList();
    }

    @PostMapping("/commandes/{commandeId}/rembourser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rembourser(@PathVariable UUID commandeId, @Valid @RequestBody Rembourser corps) {
        service.rembourser(Utilisateur.idCourantObligatoire(), commandeId, corps.motif());
    }

    @GetMapping("/associations/{associationId}/journal")
    public JournalVue journal(@PathVariable UUID associationId) {
        UUID moi = Utilisateur.idCourantObligatoire();
        return new JournalVue(
                service.solde(moi, associationId),
                service.journal(moi, associationId).stream().map(EcritureVue::de).toList());
    }

    // -------------------------------------------------------------- corps

    public record CommanderAdhesion(
            @NotNull UUID campagneId,
            @NotBlank @Pattern(regexp = "ETUDIANT|EXTERIEUR|ANCIEN") String publicCible) { }

    public record Rembourser(@NotBlank @Size(max = 300) String motif) { }

    // --------------------------------------------------------------- vues

    public record CommandeVue(UUID id, UUID associationId, String statut,
                              int montantTotalCents, String devise, OffsetDateTime payeeLe) {
        static CommandeVue de(Commande c) {
            return new CommandeVue(c.getId(), c.getAssociationId(), c.getStatut().name(),
                    c.getMontantTotalCents(), c.getDevise(), c.getPayeeLe());
        }
    }

    public record SecretVue(String secretClient) { }

    public record EcritureVue(UUID id, String sens, int montantCents,
                              String motif, OffsetDateTime creeLe) {
        static EcritureVue de(EcritureLedger e) {
            return new EcritureVue(e.getId(), e.getSens().name(), e.getMontantCents(),
                    e.getMotif(), e.getCreeLe());
        }
    }

    public record JournalVue(long soldeCents, List<EcritureVue> ecritures) { }
}
