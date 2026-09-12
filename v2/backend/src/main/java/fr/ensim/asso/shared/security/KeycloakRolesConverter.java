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
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> c)) {
            return List.of();
        }
        return c.stream()
                .map(String::valueOf)
                .filter(ROLES_GLOBAUX_CONNUS::contains)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
