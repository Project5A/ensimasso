package fr.ensim.asso.shared.debit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limitation de débit sur les routes ouvertes sans jeton.
 *
 * <p>Corrige SEC-07, le seul constat de l'audit de la v1 que la réécriture
 * avait laissé ouvert — repéré en relisant l'audit plutôt qu'en relisant le
 * code, ce qui est exactement l'intérêt de le relire.
 *
 * <p><strong>Portée.</strong> Seules les routes accessibles sans identité sont
 * concernées : la lecture publique et le webhook de paiement. Le reste exige
 * déjà un jeton, et limiter au débit un bureau qui édite ses pages n'aiderait
 * personne. L'authentification elle-même est chez Keycloak, qui a sa propre
 * détection de force brute — c'est une des raisons de ne pas l'avoir réécrite.
 *
 * <p><strong>Ce que cette limite ne fait pas.</strong> Elle est par instance :
 * avec quatre répliques du profil {@code delivery}, le plafond réel est
 * quadruple. La limite qui fait autorité est celle de l'Ingress, déclarée dans
 * les manifestes ; celle-ci est une seconde barrière, qui tient même si la
 * première est mal configurée — et elle protège aussi un déploiement à une
 * seule instance, qui est le cas le plus probable pour une association.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "ensimasso.debit.actif", havingValue = "true", matchIfMissing = true)
public class LimiteurDebit extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteurDebit.class);

    private final int requetesParMinute;
    private final int clientsMax;
    private final Clock horloge;
    private final Map<String, SeauJetons> seaux;

    public LimiteurDebit(
            @Value("${ensimasso.debit.par-minute:120}") int requetesParMinute,
            @Value("${ensimasso.debit.clients-max:10000}") int clientsMax,
            Clock horloge) {
        this.requetesParMinute = requetesParMinute;
        this.clientsMax = clientsMax;
        this.horloge = horloge;
        // Borné, comme le cache : sans plafond, une rafale d'adresses usurpées
        // ferait de ce garde-fou une fuite de mémoire, c'est-à-dire une
        // deuxième façon de tomber.
        this.seaux = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SeauJetons> plusAncien) {
                return size() > LimiteurDebit.this.clientsMax;
            }
        };
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = cheminDecode(requete);
        return !(chemin.startsWith("/api/public/") || chemin.startsWith("/api/webhooks/"));
    }

    /**
     * Le chemin tel que le CONTENEUR l'a résolu, donc décodé.
     *
     * <p>Cette méthode lisait {@code getRequestURI()}, qui rend la ligne de
     * requête brute, jamais décodée. Tomcat, lui, décode avant de choisir le
     * servlet, et Spring Security décode avant d'évaluer ses règles. Encoder un
     * seul caractère suffisait donc à sortir de la limite sans sortir de la
     * route : {@code GET /api/%70ublic/associations} passe le pare-feu strict
     * (il ne rejette que {@code ;}, {@code %2f} et {@code %2e}), reste
     * {@code permitAll} pour Spring Security, atteint bien le contrôleur du
     * portail — et ce filtre-ci se désactivait, parce que la chaîne
     * « /api/%70ublic/… » ne commence pas par « /api/public/ ».
     *
     * <p>Autrement dit : le plafond disparaissait sur exactement les deux
     * routes qu'il existe pour protéger. Vérifié contre un Tomcat réel, pas
     * contre une requête montée à la main — voir {@code LimiteDebitCheminTest}.
     *
     * <p>{@code getServletPath()} et {@code getPathInfo()} sont décodés par
     * spécification, et leur concaténation est le chemin dans l'application,
     * quel que soit le mappage du servlet. Le repli sur l'URI brute ne sert que
     * si le conteneur ne rend ni l'un ni l'autre — ce qui, pour une route
     * commençant par {@code /api/}, ne se produit pas.
     */
    static String cheminDecode(HttpServletRequest requete) {
        String servlet = requete.getServletPath();
        String reste = requete.getPathInfo();
        String chemin = (servlet == null ? "" : servlet) + (reste == null ? "" : reste);
        return chemin.isEmpty() ? requete.getRequestURI() : chemin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain suite) throws ServletException, IOException {
        SeauJetons seau = seauDe(requete.getRemoteAddr());
        if (seau.consommer()) {
            suite.doFilter(requete, reponse);
            return;
        }

        long attente = seau.attenteSecondes();
        log.warn("débit dépassé pour {} sur {}", requete.getRemoteAddr(), requete.getRequestURI());
        reponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        reponse.setHeader("Retry-After", Long.toString(attente));
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Sans ceci, « Trop de requêtes » part en ISO-8859-1 et arrive illisible.
        // Spring pose l'encodage tout seul sur un ProblemDetail ; un filtre qui
        // écrit lui-même dans la réponse doit le faire lui-même.
        reponse.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        reponse.getWriter().write("""
                {"type":"about:blank","title":"Trop de requêtes","status":429,\
                "detail":"Réessayez dans %d seconde(s)."}""".formatted(attente));
    }

    private synchronized SeauJetons seauDe(String client) {
        return seaux.computeIfAbsent(client,
                c -> new SeauJetons(requetesParMinute, Duration.ofMinutes(1), horloge));
    }
}
