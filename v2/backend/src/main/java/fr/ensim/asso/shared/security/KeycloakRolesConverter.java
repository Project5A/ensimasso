package fr.ensim.asso.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extrait les rôles de royaume Keycloak ({@code realm_access.roles}).
 *
 * <p>Seuls les rôles <em>globaux</em> transitent par le jeton, et la liste est
 * volontairement filtrée : un rôle inconnu de la plateforme est ignoré plutôt
 * que promu en autorité Spring. Les droits par association ne sont pas ici.
 */
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> ROLES_GLOBAUX_CONNUS =
            Set.of("PLATFORM_ADMIN", "STUDENT", "ALUMNI");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // `getClaim` fait un transtypage NON CONTRÔLÉ : affecter le résultat à
        // un Map insérait un checkcast, et la garde qui suivait ne portait que
        // sur la nullité puis sur la forme de `roles`. La forme de
        // `realm_access` lui-même n'était jamais vérifiée. Un jeton signé
        // valide dont cette revendication est une chaîne ou une liste — ce
        // qu'un mapper Keycloak mal réglé, ou un autre émetteur branché sur
        // OIDC_ISSUER, produit sans effort — levait donc une
        // ClassCastException à l'intérieur du convertisseur. Ce n'est pas une
        // AuthenticationException : elle traversait la chaîne de filtres et
        // sortait en 500, là où toutes les autres formes dégradées rendent une
        // liste vide, donc un 403. Un 500 réveille une astreinte et fait
        // réessayer l'appelant ; un 403 dit la vérité.
        if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        if (!(realmAccess.get("roles") instanceof Collection<?> c)) {
            return List.of();
        }
        return c.stream()
                .map(String::valueOf)
                .filter(ROLES_GLOBAUX_CONNUS::contains)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
