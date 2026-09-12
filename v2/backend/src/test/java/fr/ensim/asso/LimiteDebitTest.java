package fr.ensim.asso;

import fr.ensim.asso.shared.debit.LimiteurDebit;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * SEC-07 : « aucune limitation de débit nulle part ».
 *
 * <p>Le seul constat de l'audit de la v1 que la réécriture avait laissé ouvert.
 * Il a été retrouvé en relisant l'audit, pas le code — ce qui est précisément
 * l'intérêt de relire l'audit.
 */
class LimiteDebitTest {

    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-09-12T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
        void avancerDe(Duration d) { maintenant = maintenant.plus(d); }
    }

    private HorlogeReglable horloge;
    private LimiteurDebit limiteur;

    @BeforeEach
    void avant() {
        horloge = new HorlogeReglable();
        limiteur = new LimiteurDebit(3, 100, horloge);
    }

    private MockHttpServletResponse appeler(String chemin, String ip) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", chemin);
        requete.setRemoteAddr(ip);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        limiteur.doFilter(requete, reponse, mock(FilterChain.class));
        return reponse;
    }

    @Test
    @DisplayName("au-delà du plafond, la requête est refusée avec un Retry-After")
    void plafondApplique() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(appeler("/api/public/associations", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse refus = appeler("/api/public/associations", "10.0.0.1");
        assertThat(refus.getStatus()).isEqualTo(429);
        assertThat(refus.getHeader("Retry-After")).isNotNull();
        assertThat(refus.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        // Sans encodage déclaré, « requêtes » arrive illisible chez le client :
        // constaté sur l'application en fonctionnement, pas déduit.
        assertThat(refus.getContentAsString()).contains("Trop de requêtes");
    }

    @Test
    @DisplayName("les jetons se reconstituent progressivement, pas par à-coups")
    void reconstitutionProgressive() throws Exception {
        for (int i = 0; i < 3; i++) {
            appeler("/api/public/associations", "10.0.0.1");
        }
        assertThat(appeler("/api/public/associations", "10.0.0.1").getStatus()).isEqualTo(429);

        // Un tiers de minute rend un jeton sur trois : une fenêtre fixe aurait
        // tout rendu d'un coup, et laissé passer 2N requêtes à sa frontière.
        horloge.avancerDe(Duration.ofSeconds(21));
        assertThat(appeler("/api/public/associations", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(appeler("/api/public/associations", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("un client bruyant n'empêche pas les autres de passer")
    void isolationParClient() throws Exception {
        for (int i = 0; i < 4; i++) {
            appeler("/api/public/associations", "10.0.0.1");
        }
        assertThat(appeler("/api/public/associations", "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(appeler("/api/public/associations", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "{0} est soumis à la limite")
    @ValueSource(strings = { "/api/public/associations/bde", "/api/webhooks/stripe" })
    @DisplayName("les routes ouvertes sans jeton sont couvertes")
    void routesOuvertesCouvertes(String chemin) throws Exception {
        for (int i = 0; i < 3; i++) {
            appeler(chemin, "10.0.0.5");
        }
        assertThat(appeler(chemin, "10.0.0.5").getStatus())
                .as("%s est accessible sans identité : elle doit être limitée", chemin)
                .isEqualTo(429);
    }

    @ParameterizedTest(name = "{0} n'est PAS bridé")
    @ValueSource(strings = { "/api/contenu/types-blocs", "/api/agenda/evenements/x",
                             "/actuator/health" })
    @DisplayName("les routes authentifiées ne sont pas bridées")
    void routesAuthentifieesLibres(String chemin) throws Exception {
        // Brider un bureau qui édite ses pages n'aiderait personne : ces routes
        // exigent déjà un jeton, et l'authentification est chez Keycloak, qui a
        // sa propre détection de force brute.
        for (int i = 0; i < 20; i++) {
            assertThat(appeler(chemin, "10.0.0.6").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("une requête acceptée poursuit bien la chaîne de filtres")
    void chaineAppelee() throws Exception {
        FilterChain suite = mock(FilterChain.class);
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/api/public/x");
        requete.setRemoteAddr("10.0.0.9");
        limiteur.doFilter(requete, new MockHttpServletResponse(), suite);
        verify(suite, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("le nombre de clients suivis est borné : pas de fuite par usurpation")
    void memoireBornee() throws Exception {
        LimiteurDebit petit = new LimiteurDebit(1, 10, horloge);
        for (int i = 0; i < 200; i++) {
            MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/public/x");
            r.setRemoteAddr("10.0.0." + i);
            petit.doFilter(r, new MockHttpServletResponse(), mock(FilterChain.class));
        }
        // Sans plafond, une rafale d'adresses usurpées ferait de ce garde-fou
        // une seconde façon de tomber.
        assertThat(nombreDeSeaux(petit)).isLessThanOrEqualTo(10);
    }

    @SuppressWarnings("unchecked")
    private int nombreDeSeaux(LimiteurDebit limiteur) throws Exception {
        var champ = LimiteurDebit.class.getDeclaredField("seaux");
        champ.setAccessible(true);
        return ((java.util.Map<String, ?>) champ.get(limiteur)).size();
    }
}
