package fr.ensim.asso;

import fr.ensim.asso.shared.debit.LimiteurDebit;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La limitation de débit, contre un VRAI conteneur.
 *
 * <p>Ce fichier existe parce qu'une requête montée à la main ne pouvait pas
 * attraper le défaut. {@code LimiteurDebit.shouldNotFilter} décidait sur
 * {@code getRequestURI()} — la ligne de requête BRUTE — alors que Tomcat décode
 * avant de choisir le servlet et que Spring Security décode avant d'évaluer ses
 * règles. Encoder un seul caractère suffisait donc à sortir de la limite sans
 * sortir de la route : {@code /api/%70ublic/associations} reste
 * {@code permitAll}, atteint bien le contrôleur du portail, et n'était plus
 * comptée. Le plafond disparaissait sur exactement les deux routes qu'il existe
 * pour protéger — et {@code LimiteDebitTest}, qui pose lui-même l'URI de ses
 * requêtes, ne pouvait pas le voir : son montage lui donnait la réponse.
 *
 * <p>On démarre donc un Tomcat, et c'est LUI qui décode.
 */
class LimiteDebitCheminTest {

    private static final int PLAFOND = 3;

    private static Tomcat tomcat;
    private static int port;
    private static HorlogeReglable horloge;

    /**
     * Toutes les requêtes viennent de 127.0.0.1, donc du MÊME seau : sans
     * horloge réglable, le premier cas viderait le seau et les suivants
     * liraient 429 sans rien avoir éprouvé. On l'avance d'une fenêtre entre
     * chaque cas.
     */
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-09-18T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
        void avancerDe(Duration d) { maintenant = maintenant.plus(d); }
    }

    /** Répond 200 et rien d'autre : ce qui est éprouvé est le filtre, pas elle. */
    private static final class ServletMuette extends HttpServlet {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse rep) throws IOException {
            rep.setStatus(200);
            rep.getWriter().write("ok");
        }
    }

    @BeforeAll
    static void demarrer() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat-debit").toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        var contexte = tomcat.addContext("", null);
        Tomcat.addServlet(contexte, "muette", new ServletMuette());
        // Mappé sur « / », comme le DispatcherServlet de Spring Boot.
        contexte.addServletMappingDecoded("/", "muette");

        var filtre = new org.apache.tomcat.util.descriptor.web.FilterDef();
        filtre.setFilterName("debit");
        horloge = new HorlogeReglable();
        filtre.setFilter(new LimiteurDebit(PLAFOND, 100, horloge));
        contexte.addFilterDef(filtre);
        var mappage = new org.apache.tomcat.util.descriptor.web.FilterMap();
        mappage.setFilterName("debit");
        mappage.addURLPattern("/*");
        contexte.addFilterMap(mappage);

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @BeforeEach
    void seauNeuf() {
        horloge.avancerDe(Duration.ofMinutes(5));
    }

    @AfterAll
    static void arreter() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    /** Les codes des N appels successifs, depuis une même adresse (localhost). */
    private List<Integer> codes(String uriBrute, int appels) throws Exception {
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < appels; i++) {
            HttpURLConnection c = (HttpURLConnection)
                    URI.create("http://localhost:" + port + uriBrute).toURL().openConnection();
            c.setRequestMethod("GET");
            codes.add(c.getResponseCode());
            try (InputStream flux = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()) {
                if (flux != null) {
                    flux.readAllBytes();
                }
            }
            c.disconnect();
        }
        return codes;
    }

    @Test
    @DisplayName("la route publique écrite en clair est bien plafonnée")
    void routePubliqueLitterale() throws Exception {
        assertThat(codes("/api/public/associations", PLAFOND + 2))
                .containsExactly(200, 200, 200, 429, 429);
    }

    @Test
    @DisplayName("un seul caractère encodé ne fait plus sortir de la limite")
    void routePubliqueEncodee() throws Exception {
        // %70 == 'p'. Tomcat décode et sert /api/public/associations ; Spring
        // Security décode et applique permitAll ; le pare-feu strict laisse
        // passer (il ne rejette que « ; », %2f et %2e). Le filtre doit compter
        // cette requête comme n'importe quelle autre.
        assertThat(codes("/api/%70ublic/associations", PLAFOND + 2))
                .as("le plafond doit tenir sur le chemin DÉCODÉ, pas sur l'URI brute")
                .containsExactly(200, 200, 200, 429, 429);
    }

    @Test
    @DisplayName("le webhook encodé non plus")
    void webhookEncode() throws Exception {
        assertThat(codes("/api/%77ebhooks/stripe", PLAFOND + 2))
                .containsExactly(200, 200, 200, 429, 429);
    }

    @Test
    @DisplayName("une route authentifiée reste hors du plafond, encodée ou non")
    void routeAuthentifieeLibre() throws Exception {
        // Le contrôle doit rester une décision sur la ROUTE, pas un plafond
        // global : brider un bureau qui édite ses pages n'aiderait personne.
        assertThat(codes("/api/contenu/types-blocs", 8))
                .containsOnly(200);
        assertThat(codes("/api/%63ontenu/types-blocs", 8))
                .containsOnly(200);
    }
}
