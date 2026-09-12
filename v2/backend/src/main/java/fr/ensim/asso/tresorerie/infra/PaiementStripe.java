package fr.ensim.asso.tresorerie.infra;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import fr.ensim.asso.tresorerie.domain.PortPaiement;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adaptateur Stripe.
 *
 * <p>Deux choses que la v1 ne faisait pas, et qui sont l'essentiel :
 * <ul>
 *   <li>le montant vient de l'appelant <em>serveur</em>, calculé depuis les
 *       tarifs — jamais du corps de la requête HTTP ;</li>
 *   <li>le webhook est vérifié cryptographiquement. Sans cela, n'importe qui
 *       peut poster un faux « paiement réussi » et s'offrir ce qu'il veut.
 *       La v1 n'avait aucun webhook : elle croyait le navigateur sur parole.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ensimasso.paiement.type", havingValue = "stripe", matchIfMissing = true)
public class PaiementStripe implements PortPaiement {

    private final String cleSecrete;
    private final String secretWebhook;

    public PaiementStripe(@Value("${ensimasso.paiement.cle-secrete:}") String cleSecrete,
                          @Value("${ensimasso.paiement.secret-webhook:}") String secretWebhook) {
        this.cleSecrete = cleSecrete;
        this.secretWebhook = secretWebhook;
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = cleSecrete;
    }

    @Override
    public Intention creerIntention(int montantCents, String devise, String referenceCommande) {
        try {
            PaymentIntent intent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount((long) montantCents)     // décidé par le serveur
                    .setCurrency(devise.toLowerCase())
                    .putMetadata("commande", referenceCommande)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true).build())
                    .build());
            return new Intention(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new IllegalStateException("création de l'intention de paiement impossible", e);
        }
    }

    @Override
    public EvenementRecu verifierEtLire(String charge, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new SignatureInvalideException("en-tête de signature absent");
        }
        Event evenement;
        try {
            evenement = Webhook.constructEvent(charge, signature, secretWebhook);
        } catch (SignatureVerificationException e) {
            throw new SignatureInvalideException("signature de webhook invalide");
        } catch (RuntimeException e) {
            // Le SDK analyse le JSON AVANT de vérifier la signature : un corps
            // malformé remonte donc en JsonSyntaxException, pas en
            // SignatureVerificationException. Sans ce filet, toute requête mal
            // formée devient un 500 — et Stripe réessaie indéfiniment un
            // évènement qui ne passera jamais.
            throw new SignatureInvalideException("charge utile de webhook illisible");
        }

        Object objet = evenement.getDataObjectDeserializer().getObject().orElse(null);
        if (!(objet instanceof PaymentIntent intent)) {
            // Évènement qui ne nous concerne pas : identifié, mais sans paiement.
            return new EvenementRecu(evenement.getId(), evenement.getType(), null, null, 0, null);
        }

        Map<String, String> meta = intent.getMetadata();
        return new EvenementRecu(
                evenement.getId(),
                evenement.getType(),
                intent.getId(),
                meta == null ? null : meta.get("commande"),
                intent.getAmountReceived() != null
                        ? intent.getAmountReceived().intValue()
                        : intent.getAmount().intValue(),
                intent.getCurrency() == null ? "eur" : intent.getCurrency());
    }

    @Override
    public void rembourser(String referencePaiement, int montantCents) {
        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(referencePaiement)
                    .setAmount((long) montantCents)
                    .build());
        } catch (StripeException e) {
            throw new IllegalStateException("remboursement impossible", e);
        }
    }
}
