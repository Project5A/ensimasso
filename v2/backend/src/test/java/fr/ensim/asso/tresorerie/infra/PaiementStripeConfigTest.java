package fr.ensim.asso.tresorerie.infra;

import com.stripe.Stripe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les réglages réseau du prestataire de paiement.
 *
 * <p>Dans le paquet de l'adaptateur : {@code init()} n'est pas public, et on
 * n'élargit pas la visibilité d'une classe de production pour la tester.
 */
class PaiementStripeConfigTest {

    @Test
    @DisplayName("sans secret, l'adaptateur refuse de se construire, et dit lequel manque")
    void secretsObligatoires() {
        // « Jamais de valeur par défaut pour un secret », dit application.yml
        // au-dessus de ces deux propriétés — qui en avaient chacune une : la
        // chaîne vide. L'application démarrait donc, et Webhook.constructEvent
        // rejetait 100 % des évènements en signature invalide : Stripe
        // encaissait, aucune adhésion ne s'activait, et rien ne le disait.
        assertThatThrownBy(() -> new PaiementStripe("", "whsec_x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_CLE_SECRETE");

        assertThatThrownBy(() -> new PaiementStripe("sk_test_x", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_WEBHOOK");

        // Les deux manquants sont nommés d'un coup : corriger un .env à raison
        // d'un redémarrage par variable serait une punition.
        assertThatThrownBy(() -> new PaiementStripe(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_CLE_SECRETE")
                .hasMessageContaining("STRIPE_SECRET_WEBHOOK");

        assertThatCode(() -> new PaiementStripe("sk_test_x", "whsec_x"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("les délais réseau sont bornés : le pool de connexions en dépend")
    void delaisBornes() {
        // Les appels au prestataire partent depuis des méthodes
        // transactionnelles, donc en tenant une connexion du pool — dix au
        // total, volontairement, parce que plusieurs répliques passent derrière
        // PgBouncer. Aux délais par défaut du SDK (30 s d'établissement, 80 s
        // de lecture), dix paiements qui traînent assèchent le pool et arrêtent
        // TOUTE l'application, pas seulement le paiement. La revue
        // d'architecture désignait déjà l'épuisement des connexions comme la
        // panne la plus probable un soir de gala.
        new PaiementStripe("sk_test_bidon", "cle-de-signature-pour-les-tests").init();

        assertThat(Stripe.getConnectTimeout())
                .as("délai d'établissement borné")
                .isPositive().isLessThanOrEqualTo(5_000);
        assertThat(Stripe.getReadTimeout())
                .as("délai de lecture borné")
                .isPositive().isLessThanOrEqualTo(15_000);
        assertThat(Stripe.getMaxNetworkRetries())
                .as("chaque reprise multiplie le temps passé à tenir une connexion")
                .isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("la clé d'idempotence est déterministe : un réessai ne double pas l'effet")
    void cleDeterministe() {
        // Les appels partent depuis des méthodes transactionnelles. Quand le
        // COMMIT échoue APRÈS l'appel — connexion coupée, délai dépassé — le
        // remboursement, lui, a bien eu lieu : Stripe n'a pas de rollback. Sans
        // clé, le réessai en crée un SECOND ; avec une clé stable, Stripe rend
        // le résultat du premier et la base rattrape au lieu de doubler.
        var premiere = PaiementStripe.idempotence("remboursement", "pi_123", 1500);
        var seconde = PaiementStripe.idempotence("remboursement", "pi_123", 1500);

        assertThat(premiere.getIdempotencyKey())
                .as("deux appels identiques doivent porter la MÊME clé")
                .isEqualTo(seconde.getIdempotencyKey())
                .isNotNull();
    }

    @Test
    @DisplayName("deux opérations distinctes ne partagent pas la même clé")
    void cleDiscriminante() {
        String remb1500 = PaiementStripe.idempotence("remboursement", "pi_123", 1500)
                .getIdempotencyKey();

        // Deux remboursements PARTIELS différents sur le même paiement sont
        // deux opérations distinctes : les confondre serait pire que de ne rien
        // faire, puisque le second ne partirait jamais.
        assertThat(PaiementStripe.idempotence("remboursement", "pi_123", 500).getIdempotencyKey())
                .isNotEqualTo(remb1500);
        // Un autre paiement, évidemment.
        assertThat(PaiementStripe.idempotence("remboursement", "pi_999", 1500).getIdempotencyKey())
                .isNotEqualTo(remb1500);
        // Et une création d'intention n'est pas un remboursement, même montant,
        // même référence : sans le préfixe d'opération, les deux se
        // confondraient et le second appel rendrait le résultat du premier.
        assertThat(PaiementStripe.idempotence("intention", "pi_123", 1500).getIdempotencyKey())
                .isNotEqualTo(remb1500);
    }
}
