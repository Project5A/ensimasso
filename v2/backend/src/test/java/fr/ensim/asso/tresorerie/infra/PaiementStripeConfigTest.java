package fr.ensim.asso.tresorerie.infra;

import com.stripe.Stripe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les réglages réseau du prestataire de paiement.
 *
 * <p>Dans le paquet de l'adaptateur : {@code init()} n'est pas public, et on
 * n'élargit pas la visibilité d'une classe de production pour la tester.
 */
class PaiementStripeConfigTest {

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
}
