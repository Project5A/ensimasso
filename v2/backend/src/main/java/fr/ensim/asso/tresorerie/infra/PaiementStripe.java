package fr.ensim.asso.tresorerie.infra;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

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

    private static final Logger log = LoggerFactory.getLogger(PaiementStripe.class);

    /** Défauts du SDK : 30 000 et 80 000 ms. Voir init(). */
    private static final int CONNEXION_MS = 3_000;
    private static final int LECTURE_MS = 8_000;

    private final String cleSecrete;
    private final String secretWebhook;

    public PaiementStripe(@Value("${ensimasso.paiement.cle-secrete:}") String cleSecrete,
                          @Value("${ensimasso.paiement.secret-webhook:}") String secretWebhook) {
        this.cleSecrete = cleSecrete;
        this.secretWebhook = secretWebhook;
    }

    /**
     * Délais bornés, et ce n'est pas un réglage de confort.
     *
     * <p>Les appels au prestataire partent DEPUIS des méthodes transactionnelles
     * — créer une intention, rembourser — donc en tenant une connexion du pool.
     * Le pool fait dix connexions, choisi bas volontairement parce que
     * plusieurs répliques passent derrière PgBouncer. Les délais par défaut du
     * SDK sont de 30 s pour l'établissement et 80 s pour la lecture : dix
     * requêtes de paiement qui traînent suffisent à assécher le pool, et
     * c'est alors TOUTE l'application qui s'arrête, pas seulement le paiement.
     * La revue d'architecture désignait déjà l'épuisement des connexions comme
     * la panne la plus probable un soir de gala.
     *
     * <p>Ce qui reste à faire, et qui n'est pas un réglage : sortir l'appel
     * distant de la transaction. Tant qu'il est dedans, borner le délai ne fait
     * que borner les dégâts.
     */
    @PostConstruct
    void init() {
        Stripe.apiKey = cleSecrete;
        Stripe.setConnectTimeout(CONNEXION_MS);
        Stripe.setReadTimeout(LECTURE_MS);
        // Une seule reprise : le SDK en fait trois par défaut, ce qui multiplie
        // d'autant le temps passé à tenir une connexion de base de données.
        Stripe.setMaxNetworkRetries(1);
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

    /**
     * Relit une intention existante plutôt que d'en créer une.
     *
     * <p>Le chemin de lecture en créait une NOUVELLE à chaque appel : rafraîchir
     * la page de paiement laissait derrière elle une traînée d'intentions
     * ouvertes, toutes payables, sur une seule commande.
     */
    @Override
    public Optional<String> secretClientDe(String referenceIntention) {
        try {
            return Optional.ofNullable(
                    PaymentIntent.retrieve(referenceIntention).getClientSecret());
        } catch (StripeException e) {
            return Optional.empty();
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

        var deserialiseur = evenement.getDataObjectDeserializer();
        Object objet = deserialiseur.getObject().orElse(null);
        if (objet == null) {
            // getObject() rend vide dès que le compte Stripe est sur une
            // version d'API différente de celle que le SDK épingle — ce qui
            // finit toujours par arriver, sans prévenir et sans rien changer
            // dans le dépôt. deserializeUnsafe() force la lecture du JSON tel
            // qu'il est arrivé. « Unsafe » vise la compatibilité des champs,
            // pas la sécurité : la signature, elle, a déjà été vérifiée.
            try {
                objet = deserialiseur.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException | RuntimeException e) {
                objet = null;
            }
        }

        if (!(objet instanceof PaymentIntent intent)) {
            // Ici on ne SAIT PAS ce qu'on vient de recevoir. Le dire, plutôt
            // que de rendre un évènement aux champs vides que le domaine
            // prendrait pour « compris, sans intérêt » — et classerait traité
            // définitivement. Un paiement encaissé disparaissait ainsi sans
            // jamais accorder le droit acheté, sans rejeu possible.
            log.warn("objet de données illisible pour l'évènement {} ({})",
                    evenement.getId(), evenement.getType());
            return EvenementRecu.illisible(evenement.getId(), evenement.getType());
        }

        Map<String, String> meta = intent.getMetadata();
        return EvenementRecu.lu(
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
