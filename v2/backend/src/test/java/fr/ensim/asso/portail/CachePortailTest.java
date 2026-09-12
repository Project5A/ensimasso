package fr.ensim.asso.portail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Le cache du chemin de lecture public, et la borne qui l'empêche de nuire. */
class CachePortailTest {

    /** Une horloge qu'on avance à la main : attendre dans un test est un test lent et instable. */
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-09-12T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
        void avancerDe(Duration d) { maintenant = maintenant.plus(d); }
    }

    @Test
    @DisplayName("une valeur mémorisée est relue, et comptée comme un succès")
    void lectureApresEcriture() {
        CacheMemoire cache = new CacheMemoire(new HorlogeReglable(), 500);
        cache.ecrire("a", "valeur", Duration.ofMinutes(10));

        assertThat(cache.lire("a")).contains("valeur");
        assertThat(cache.lire("b")).isEmpty();

        PortCache.Statistiques s = cache.statistiques();
        assertThat(s.succes()).isEqualTo(1);
        assertThat(s.echecs()).isEqualTo(1);
        assertThat(s.tauxDeSucces()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("une entrée expirée n'est pas servie, et ne reste pas en mémoire")
    void expiration() {
        HorlogeReglable horloge = new HorlogeReglable();
        CacheMemoire cache = new CacheMemoire(horloge, 500);
        cache.ecrire("a", "valeur", Duration.ofMinutes(10));

        horloge.avancerDe(Duration.ofMinutes(9));
        assertThat(cache.lire("a")).contains("valeur");

        horloge.avancerDe(Duration.ofMinutes(2));
        assertThat(cache.lire("a")).isEmpty();
        assertThat(cache.statistiques().entrees())
                .as("une entrée périmée doit être retirée, pas seulement ignorée")
                .isZero();
    }

    @Test
    @DisplayName("le cache est borné : il évince plutôt que de grossir sans fin")
    void borne() {
        CacheMemoire cache = new CacheMemoire(new HorlogeReglable(), 3);
        for (int i = 0; i < 10; i++) {
            cache.ecrire("cle" + i, "v", Duration.ofMinutes(10));
        }
        // Un cache sans borne est une fuite de mémoire qu'on ne découvre qu'en
        // production, et le nombre de pages publiques est connu et petit.
        assertThat(cache.statistiques().entrees()).isEqualTo(3);
        assertThat(cache.statistiques().evictions()).isEqualTo(7);
        assertThat(cache.lire("cle9")).contains("v");
        assertThat(cache.lire("cle0")).isEmpty();
    }

    @Test
    @DisplayName("la plus récemment lue survit à la plus anciennement lue")
    void evictionParUsage() {
        CacheMemoire cache = new CacheMemoire(new HorlogeReglable(), 2);
        cache.ecrire("a", "1", Duration.ofMinutes(10));
        cache.ecrire("b", "2", Duration.ofMinutes(10));
        cache.lire("a");                                  // « a » redevient récente
        cache.ecrire("c", "3", Duration.ofMinutes(10));

        assertThat(cache.lire("a")).contains("1");
        assertThat(cache.lire("b")).isEmpty();
    }

    @Test
    @DisplayName("publier change la clé : il n'y a donc rien à invalider")
    void cleVersionnee() {
        var asso = new fr.ensim.asso.gouvernance.domain.Association(
                "bde", "BDE", fr.ensim.asso.gouvernance.domain.Association.TypeAssociation.BUREAU);
        var mandat = java.util.UUID.randomUUID();
        var page = new fr.ensim.asso.contenu.domain.Page(mandat, "accueil", "Accueil", 0);
        var v1 = java.util.UUID.randomUUID();
        var v2 = java.util.UUID.randomUUID();

        String cleV1 = ServicePortail.cleDe(asso, page, v1, true);
        String cleV2 = ServicePortail.cleDe(asso, page, v2, true);

        // L'invalidation est la partie où l'on se trompe. Ici elle n'existe pas :
        // la nouvelle version écrit ailleurs, l'ancienne entrée s'éteint seule.
        assertThat(cleV1).isNotEqualTo(cleV2);

        // Et une archive ne partage pas la clé de la page en cours : le même
        // contenu ne s'y rend pas de la même façon.
        assertThat(ServicePortail.cleDe(asso, page, v1, false)).isNotEqualTo(cleV1);
    }

    @Test
    @DisplayName("la durée du cache est plafonnée par la validité des URL de médias")
    void dureePlafonnee() {
        // LE point de cette borne : une page gardée plus longtemps que ses URL
        // signées afficherait des images mortes. Ce serait STOR-01 réintroduit
        // par la porte de derrière — un bug déjà payé une fois.
        Duration validiteUrls = Duration.ofMinutes(30);

        assertThat(ServicePortail.plafonner(Duration.ofHours(6), validiteUrls))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(ServicePortail.plafonner(Duration.ofMinutes(10), validiteUrls))
                .as("une valeur raisonnable n'est pas modifiée")
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(ServicePortail.plafonner(Duration.ofHours(6), validiteUrls))
                .isLessThan(validiteUrls);
    }
}
