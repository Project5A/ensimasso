package fr.ensim.asso.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Chaîne de sécurité, en refus par défaut.
 *
 * <p>Écrite en réaction directe à l'audit de la v1, où 27 des 32 routes
 * étaient couvertes par {@code permitAll()} et où {@code @PreAuthorize} était
 * présent mais inerte faute de {@code @EnableMethodSecurity}. Ici :
 * <ul>
 *   <li>{@code anyRequest().authenticated()} est la règle ; les exceptions sont
 *       énumérées et se limitent à la lecture publique et à la supervision ;</li>
 *   <li>la sécurité de méthode est <em>réellement activée</em> ;</li>
 *   <li>aucune implémentation de JWT maison : Keycloak émet, Spring valide ;</li>
 *   <li>les en-têtes de sécurité sont posés, y compris {@code frameOptions}
 *       que la v1 désactivait globalement.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurityMarker
@EnableMethodSecurity          // sans cela, @PreAuthorize ne fait rien du tout
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter converter) throws Exception {
        http
            // API sans état consommée avec un jeton porteur : pas de session,
            // donc pas de CSRF côté serveur. Le front public est servi par le
            // même origine derrière le proxy — voir §12 de l'architecture.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())

            .authorizeHttpRequests(auth -> auth
                // --- lecture publique : uniquement le contenu PUBLIÉ ---
                .requestMatchers(HttpMethod.GET,
                        "/api/public/**").permitAll()

                // --- webhook de paiement ---
                // Ouvert sans jeton, mais PAS non authentifié : l'identité est
                // prouvée par la signature cryptographique de Stripe, vérifiée
                // dans le contrôleur avant toute lecture du contenu. C'est la
                // seule exception d'écriture de toute la chaîne.
                .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()

                // --- supervision ---
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").hasAuthority("ROLE_PLATFORM_ADMIN")
                .requestMatchers("/actuator/**").hasAuthority("ROLE_PLATFORM_ADMIN")

                // --- tout le reste exige une identité vérifiée ---
                .anyRequest().authenticated())

            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))

            .headers(h -> h
                .frameOptions(f -> f.deny())              // la v1 le désactivait
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000)));

        return http.build();
    }

    /**
     * Les rôles <em>globaux</em> (PLATFORM_ADMIN, STUDENT) viennent du jeton.
     * Les droits <em>par association</em> ne sont jamais dans le jeton : ils
     * sont résolus par {@code PolitiqueAcces}, pour rester révocables
     * immédiatement et ne pas gonfler le jeton d'une personne membre de
     * plusieurs bureaux.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        converter.setPrincipalClaimName("sub");
        return converter;
    }

}
