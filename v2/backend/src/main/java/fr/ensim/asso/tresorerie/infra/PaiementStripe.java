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
        exigerConfiguration(cleSecrete, secretWebhook);
        this.cleSecrete = cleSecrete;
        this.secretWebhook = secretWebhook;
    }

    /**
     * Sans secret, on ne démarre pas.
     *
     * <p>« Jamais de valeur par défaut pour un secret : une clé absente doit
     * faire échouer bruyamment, pas démarrer avec un placeholder », dit le
     * commentaire d'{@code application.yml} au-dessus de ces deux propriétés.
     * Elles avaient pourtant chacune une valeur par défaut — la chaîne vide —
     * et rien ne la relisait. L'application démarrait donc très bien, et :
     *
     * <ul>
     *   <li>{@code Stripe.apiKey} valant "", tout appel au prestataire échouait
     *       au premier paiement ;</li>
     *   <li>le secret de webhook valant "", {@code Webhook.constructEvent}
     *       rejetait <strong>100 % des évènements</strong> en
     *       {@code SignatureVerificationException} — c'est-à-dire, du point de
     *       vue du code, exactement ce que produit un faux webhook. Chaque
     *       paiement réellement encaissé par Stripe était compté
     *       « signature_invalide » et n'activait aucune adhésion. L'étudiant
     *       paie, l'association encaisse, et personne n'est adhérent.</li>
     * </ul>
     *
     * <p>Le silence est ce qui rend cette panne coûteuse : elle ne se voit pas
     * au démarrage, ne se voit pas à l'achat, et se découvre à la première
     * réclamation. C'est la même correction que {@code StockageS3} applique
     * déjà pour MinIO, au même endroit et pour la même raison.
     */
    private static void exigerConfiguration(String cleSecrete, String secretWebhook) {
        var manquants = new java.util.ArrayList<String>();
        if (cleSecrete == null || cleSecrete.isBlank()) manquants.add("STRIPE_CLE_SECRETE");
        if (secretWebhook == null || secretWebhook.isBlank()) manquants.add("STRIPE_SECRET_WEBHOOK");
        if (!manquants.isEmpty()) {
            throw new IllegalStateException(
                    "paiement non configuré : " + String.join(", ", manquants)
                    + (manquants.size() > 1
                            ? " sont absentes. Renseignez-les"
                            : " est absente. Renseignez-la")
                    + " dans .env (voir .env.example) puis relancez. "
                    + "Sans secret de webhook, Stripe encaisse et aucune adhésion "
                    + "ne s'active : mieux vaut ne pas démarrer.");
        }
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
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                            .setAmount((long) montantCents)     // décidé par le serveur
                            .setCurrency(devise.toLowerCase())
                            .putMetadata("commande", referenceCommande)
                            .setAutomaticPaymentMethods(
                                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                            .setEnabled(true).build())
                            .build(),
                    idempotence("intention", referenceCommande, montantCents));
            return new Intention(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new IllegalStateException("création de l'intention de paiement impossible", e);
        }
    }

    /**
     * La clé d'idempotence d'un appel, DÉTERMINISTE.
     *
     * <p>Ces appels partent depuis des méthodes transactionnelles. Quand le
     * COMMIT échoue après coup — une connexion coupée, un délai dépassé —
     * l'effet chez le prestataire, lui, a bien eu lieu : Stripe n'a pas de
     * rollback. Sans clé, le réessai crée un SECOND remboursement ou une
     * SECONDE intention ; avec une clé stable, Stripe rend le résultat du
     * premier appel et la base rattrape son retard au lieu de doubler l'effet.
     *
     * <p>Le montant fait partie de la clé : deux remboursements partiels
     * différents sur un même paiement sont deux opérations distinctes, et les
     * confondre serait pire que de ne rien faire. La clé générée par le SDK ne
     * convient pas — elle est aléatoire, donc différente à chaque appel.
     *
     * <p>Ce que cela ne répare pas, et qui est écrit ici plutôt que sous-
     * entendu : l'appel distant reste DANS la transaction. La clé borne les
     * dégâts d'un réessai ; sortir l'appel de la transaction demanderait une
     * machine à états persistée, que l'architecture a délibérément refusée.
     */
    static com.stripe.net.RequestOptions idempotence(String operation, String reference, int montantCents) {
        return com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(operation + "-" + reference + "-" + montantCents)
                .build();
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

    /**
     * Ferme une intention encore ouverte chez Stripe.
     *
     * <p>Le statut est relu AVANT d'annuler, et non déduit de l'échec de
     * l'annulation : Stripe refuse d'annuler une intention aboutie, mais il
     * refuse aussi pour d'autres raisons, et confondre « déjà payé » avec
     * « appel en échec » ferait répondre « réessayez » à quelqu'un dont
     * l'argent est déjà parti.
     */
    @Override
    public void annulerIntention(String referenceIntention) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(referenceIntention);
            String statut = intent.getStatus();
            if ("succeeded".equals(statut) || "processing".equals(statut)) {
                throw new PaiementEngageException(
                        "le paiement de cette intention est " + statut
                      + " : elle ne s'annule plus, elle se rembourse");
            }
            if ("canceled".equals(statut)) {
                return;                      // déjà close : rejouer ne casse rien
            }
            intent.cancel();
        } catch (StripeException e) {
            throw new IllegalStateException(
                    "annulation de l'intention de paiement impossible", e);
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
            Refund.create(
                    RefundCreateParams.builder()
                            .setPaymentIntent(referencePaiement)
                            .setAmount((long) montantCents)
                            .build(),
                    idempotence("remboursement", referencePaiement, montantCents));
        } catch (StripeException e) {
            throw new IllegalStateException("remboursement impossible", e);
        }
    }
}
