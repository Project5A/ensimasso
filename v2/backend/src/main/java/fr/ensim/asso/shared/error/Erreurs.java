package fr.ensim.asso.shared.error;

import java.util.List;
import java.util.UUID;

/**
 * Les types d'erreur partagés par tous les modules.
 *
 * <p>Ils vivent ici pour que le gestionnaire global n'ait besoin de connaître
 * aucun module : sans cela {@code shared} dépendrait de {@code contenu}, qui
 * dépend de {@code shared} — un cycle que la vérification de modules refuse,
 * et à juste titre.
 */
public final class Erreurs {

    private Erreurs() { }

    /** La ressource demandée n'existe pas. → 404 */
    public static class Introuvable extends RuntimeException {
        public Introuvable(String quoi, Object id) {
            super(quoi + " introuvable : " + id);
        }
    }

    /** L'état courant interdit l'opération. → 409 */
    public static class Conflit extends RuntimeException {
        public Conflit(String message) { super(message); }
    }

    /** La politique d'accès refuse. → 403 */
    public static class AccesRefuse extends RuntimeException {
        public AccesRefuse(String message) { super(message); }
    }

    /** Le contenu soumis est structurellement invalide. → 422 */
    public static class ContenuInvalide extends RuntimeException {
        private final List<String> details;
        public ContenuInvalide(String message, List<String> details) {
            super(message);
            this.details = List.copyOf(details);
        }
        public List<String> getDetails() { return details; }
    }

    /** La requête référence quelque chose d'inconnu du système. → 400 */
    public static class RequeteInvalide extends RuntimeException {
        public RequeteInvalide(String message) { super(message); }
    }
}
