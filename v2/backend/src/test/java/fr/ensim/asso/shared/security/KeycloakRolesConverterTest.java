package fr.ensim.asso.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La traduction d'un jeton en autorités Spring.
 *
 * <p>C'est la racine de toute décision d'autorisation, et rien ne l'éprouvait.
 * Le convertisseur lisait {@code realm_access} par un transtypage non contrôlé
 * puis ne vérifiait que la forme de {@code roles} : un jeton SIGNÉ VALIDE dont
 * cette revendication n'est pas un objet JSON — ce qu'un mapper Keycloak mal
 * réglé, ou un autre émetteur branché sur {@code OIDC_ISSUER}, produit sans
 * effort — levait une {@code ClassCastException} au milieu de la chaîne de
 * filtres. Ce n'est pas une {@code AuthenticationException} : elle sortait en
 * 500, là où toutes les autres formes dégradées rendent 403.
 */
class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter convertisseur = new KeycloakRolesConverter();

    /** Un jeton signé valide, dont on choisit la seule revendication qui compte. */
    private Jwt jeton(Object realmAccess) {
        Jwt.Builder b = Jwt.withTokenValue("jeton")
                .header("alg", "RS256")
                .subject("etudiant")
                .issuedAt(Instant.parse("2026-09-18T09:00:00Z"))
                .expiresAt(Instant.parse("2026-09-18T10:00:00Z"));
        if (realmAccess != null) {
            b = b.claim("realm_access", realmAccess);
        }
        return b.build();
    }

    private List<String> autorites(Object realmAccess) {
        return convertisseur.convert(jeton(realmAccess)).stream()
                .map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test
    @DisplayName("les rôles connus deviennent des autorités préfixées")
    void rolesConnus() {
        assertThat(autorites(Map.of("roles", List.of("STUDENT", "PLATFORM_ADMIN"))))
                .containsExactly("ROLE_PLATFORM_ADMIN", "ROLE_STUDENT");
    }

    @Test
    @DisplayName("un rôle inconnu de la plateforme est ignoré, pas promu")
    void roleInconnuIgnore() {
        // Keycloak distribue des rôles par défaut (offline_access,
        // uma_authorization, default-roles-…) qui n'ont aucun sens ici. Les
        // promouvoir en autorités ferait dépendre nos règles de la
        // configuration d'un autre système.
        assertThat(autorites(Map.of("roles",
                List.of("STUDENT", "offline_access", "uma_authorization", "ADMIN"))))
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("un jeton sans realm_access ne porte aucune autorité")
    void sansRevendication() {
        assertThat(autorites(null)).isEmpty();
    }

    @Test
    @DisplayName("un realm_access qui n'est pas un objet ne fait plus tomber la requête")
    void realmAccessMalForme() {
        // C'EST le défaut : ces deux formes levaient une ClassCastException,
        // donc un 500, avant même d'atteindre la moindre règle d'autorisation.
        assertThatCode(() -> autorites("je-suis-une-chaine")).doesNotThrowAnyException();
        assertThatCode(() -> autorites(List.of("STUDENT"))).doesNotThrowAnyException();

        assertThat(autorites("je-suis-une-chaine")).isEmpty();
        assertThat(autorites(List.of("STUDENT")))
                .as("une liste au lieu d'un objet ne doit surtout pas accorder le rôle")
                .isEmpty();
    }

    @Test
    @DisplayName("un roles qui n'est pas une liste ne porte aucune autorité")
    void rolesMalForme() {
        assertThat(autorites(Map.of("roles", "STUDENT"))).isEmpty();
        assertThat(autorites(Map.of("autre", List.of("STUDENT")))).isEmpty();
    }

    @Test
    @DisplayName("une entrée non textuelle dans roles ne casse rien")
    void roleNonTextuel() {
        assertThat(autorites(Map.of("roles", List.of(42, "STUDENT"))))
                .containsExactly("ROLE_STUDENT");
    }
}
