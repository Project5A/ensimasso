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

    /**
     * Les rôles GLOBAUX portés par le jeton vérifié, sans le préfixe « ROLE_ ».
     *
     * <p>Lire des droits dans le jeton est proscrit partout ailleurs : les
     * droits par association vivent en base, pour être révocables
     * immédiatement. Ces rôles-ci sont d'une autre nature — « est étudiant »,
     * « est ancien » — et l'application ne les connaît pas et ne peut pas les
     * connaître : c'est l'annuaire de l'école qui les détient, et le jeton est
     * ce qui les transporte, signé.
     */
    public static java.util.Set<String> rolesGlobaux() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return java.util.Set.of();
        }
        return a.getAuthorities().stream()
                .map(Object::toString)
                .filter(r -> r.startsWith("ROLE_"))
                .map(r -> r.substring("ROLE_".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** L'identité courante, ou une exception : à utiliser sur les routes authentifiées. */
    public static UUID idCourantObligatoire() {
        return idCourant().orElseThrow(() ->
                new IllegalStateException("aucune identité dans le contexte de sécurité"));
    }
}
