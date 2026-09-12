package fr.ensim.asso.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/** Accès à l'identité courante. Le « sub » Keycloak est l'identifiant de personne. */
public final class Utilisateur {

    private Utilisateur() { }

    public static Optional<UUID> idCourant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || !(a.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** L'identité courante, ou une exception : à utiliser sur les routes authentifiées. */
    public static UUID idCourantObligatoire() {
        return idCourant().orElseThrow(() ->
                new IllegalStateException("aucune identité dans le contexte de sécurité"));
    }
}
