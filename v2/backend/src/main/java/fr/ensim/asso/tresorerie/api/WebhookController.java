package fr.ensim.asso.tresorerie.api;

import fr.ensim.asso.tresorerie.app.ServiceTresorerie;
import fr.ensim.asso.tresorerie.domain.PortPaiement;
import io.micrometer.core.instrument.MeterRegistry;
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

    /**
     * Le compteur est ici et non dans le service, parce que c'est ici qu'on
     * voit tout : les signatures invalides, que le service ne connaît pas, et
     * les échecs de traitement, qu'il signale en levant. Une rafale de
     * « signature_invalide » est soit une clé de webhook périmée, soit
     * quelqu'un qui tente de s'offrir une adhésion — les deux méritent qu'on
     * le sache le jour même.
     */
    private final MeterRegistry metriques;

    public WebhookController(PortPaiement prestataire, ServiceTresorerie service,
                             MeterRegistry metriques) {
        this.prestataire = prestataire;
        this.service = service;
        this.metriques = metriques;
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
            compter("signature_invalide");
            return ResponseEntity.badRequest().body("signature invalide");
        }

        ServiceTresorerie.ResultatWebhook resultat;
        try {
            resultat = service.traiter(evenement);
        } catch (RuntimeException e) {
            compter("echec");
            throw e;                       // 500 : Stripe réessaiera, et il le doit
        }
        compter(resultat.name().toLowerCase(java.util.Locale.ROOT));
        log.info("webhook {} ({}) : {}", evenement.id(), evenement.type(), resultat);
        return ResponseEntity.status(HttpStatus.OK).body(resultat.name());
    }

    private void compter(String resultat) {
        metriques.counter("ensimasso.tresorerie.webhook", "resultat", resultat).increment();
    }
}
