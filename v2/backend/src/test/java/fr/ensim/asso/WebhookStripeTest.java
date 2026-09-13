package fr.ensim.asso;

import fr.ensim.asso.tresorerie.domain.PortPaiement;
import fr.ensim.asso.tresorerie.infra.PaiementStripe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le webhook Stripe, traversé par son VRAI adaptateur.
 *
 * <p>Le test précédent définissait une classe anonyme dans le corps du test,
 * y réécrivait la logique qu'il prétendait vérifier, puis asseyait ses
 * assertions dessus. Il passait au vert sans qu'une seule ligne de
 * {@link PaiementStripe} ne soit exécutée : supprimer l'adaptateur entier ne
 * l'aurait pas fait échouer.
 *
 * <p>Ici les charges utiles sont signées pour de bon, avec le secret que
 * l'adaptateur reçoit — c'est la seule façon d'atteindre le code qui se trouve
 * derrière la vérification de signature.
 */
class WebhookStripeTest {

    private static final String SECRET = "whsec_test_0123456789abcdef";
    private final PaiementStripe stripe = new PaiementStripe("sk_test_bidon", SECRET);

    /** Reproduit le schéma de signature de Stripe : {@code t=<ts>,v1=<hmac>}. */
    private static String signer(String charge) {
        long t = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] brut = mac.doFinal((t + "." + charge).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : brut) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + t + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String evenement(String objetJson) {
        return """
                {"id":"evt_1","object":"event","api_version":"2020-08-27",
                 "type":"payment_intent.succeeded",
                 "data":{"object":%s}}
                """.formatted(objetJson);
    }

    private PortPaiement.EvenementRecu lire(String charge) {
        return stripe.verifierEtLire(charge, signer(charge));
    }

    @Test
    @DisplayName("un paiement réussi est lu : référence, commande et montant encaissé")
    void paiementLu() {
        var recu = lire(evenement("""
                {"id":"pi_123","object":"payment_intent","amount":1500,
                 "amount_received":1500,"currency":"eur",
                 "metadata":{"commande":"c0ffee00-0000-0000-0000-000000000001"}}"""));

        assertThat(recu.objetLisible()).isTrue();
        assertThat(recu.referencePaiement()).isEqualTo("pi_123");
        assertThat(recu.referenceCommande()).isEqualTo("c0ffee00-0000-0000-0000-000000000001");
        assertThat(recu.montantCents())
                .as("le montant ENCAISSÉ, pas le montant demandé")
                .isEqualTo(1500);
    }

    @Test
    @DisplayName("un montant partiellement encaissé remonte le montant reçu, pas le montant dû")
    void montantPartiel() {
        var recu = lire(evenement("""
                {"id":"pi_124","object":"payment_intent","amount":1500,
                 "amount_received":900,"currency":"eur","metadata":{}}"""));

        assertThat(recu.montantCents()).isEqualTo(900);
        assertThat(recu.referenceCommande()).isNull();
    }

    @Test
    @DisplayName("un objet de données illisible est SIGNALÉ, pas rendu comme un évènement vide")
    void objetIllisible() {
        // Une version d'API différente entre le compte Stripe et le SDK suffit
        // à ce que getObject() rende vide. L'adaptateur rendait alors un
        // évènement aux champs nuls, que le domaine classait « compris, sans
        // intérêt » et marquait traité pour toujours : paiement encaissé,
        // droit jamais accordé, rejeu impossible.
        var recu = lire(evenement("""
                {"id":"obj_1","object":"un_objet_que_le_sdk_ne_connait_pas","zzz":1}"""));

        assertThat(recu.objetLisible())
                .as("l'évènement est identifié, mais son contenu n'a pas été compris")
                .isFalse();
        assertThat(recu.id()).isEqualTo("evt_1");
        assertThat(recu.type()).isEqualTo("payment_intent.succeeded");
    }

    @Test
    @DisplayName("une signature absente ou invalide est refusée")
    void signatureRefusee() {
        String charge = evenement("""
                {"id":"pi_125","object":"payment_intent","amount":100,
                 "amount_received":100,"currency":"eur","metadata":{}}""");

        assertThatThrownBy(() -> stripe.verifierEtLire(charge, null))
                .isInstanceOf(PortPaiement.SignatureInvalideException.class)
                .hasMessageContaining("absent");

        assertThatThrownBy(() -> stripe.verifierEtLire(charge, "t=1,v1=deadbeef"))
                .isInstanceOf(PortPaiement.SignatureInvalideException.class);
    }

    @Test
    @DisplayName("un corps malformé est rejeté en signature invalide, jamais en 500")
    void corpsMalforme() {
        // Le SDK analyse le JSON AVANT de vérifier la signature : un corps
        // malformé remonte en JsonSyntaxException, pas en
        // SignatureVerificationException. Sans filet, Stripe réessaierait
        // indéfiniment un évènement qui ne passera jamais.
        assertThatThrownBy(() -> lire("{pas du json"))
                .isInstanceOf(PortPaiement.SignatureInvalideException.class)
                .hasMessageContaining("illisible");
    }
}
