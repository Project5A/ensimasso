package fr.ensim.asso.tresorerie.api;

import fr.ensim.asso.tresorerie.app.ServiceTresorerie;
import fr.ensim.asso.tresorerie.domain.PortPaiement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Le webhook de paiement.
 *
 * <p>La seule route de l'application ouverte sans jeton — et elle n'est pas
 * pour autant non authentifiée : l'authentification est la <strong>signature
 * cryptographique</strong> de Stripe, vérifiée avant toute lecture du contenu.
 * Sans elle, poster un faux « paiement réussi » suffirait à s'offrir n'importe
 * quoi ; la v1 n'avait aucun webhook et croyait le navigateur sur parole.
 *
 * <p>On répond 200 même sur un évènement ignoré ou rejoué : un code d'erreur
 * ferait réessayer Stripe indéfiniment pour rien. On ne répond 400 que sur une
 * signature invalide, et 500 seulement si le traitement a réellement échoué —
 * là, un réessai est souhaitable.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PortPaiement prestataire;
    private final ServiceTresorerie service;

    public WebhookController(PortPaiement prestataire, ServiceTresorerie service) {
        this.prestataire = prestataire;
        this.service = service;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripe(@RequestBody String charge,
                                         @RequestHeader(value = "Stripe-Signature", required = false)
                                         String signature) {
        PortPaiement.EvenementRecu evenement;
        try {
            evenement = prestataire.verifierEtLire(charge, signature);
        } catch (PortPaiement.SignatureInvalideException e) {
            // Ni le contenu ni l'erreur ne sont détaillés : une signature
            // invalide est soit une mauvaise configuration, soit une attaque.
            log.warn("webhook rejeté : {}", e.getMessage());
            return ResponseEntity.badRequest().body("signature invalide");
        }

        ServiceTresorerie.ResultatWebhook resultat = service.traiter(evenement);
        log.info("webhook {} ({}) : {}", evenement.id(), evenement.type(), resultat);
        return ResponseEntity.status(HttpStatus.OK).body(resultat.name());
    }
}
