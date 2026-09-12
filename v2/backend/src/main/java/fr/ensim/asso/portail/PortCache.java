package fr.ensim.asso.portail;

import java.time.Duration;
import java.util.Optional;

/**
 * Le cache du chemin de lecture public.
 *
 * <p>Il n'y a <strong>pas</strong> d'opération d'invalidation, et ce n'est pas
 * un oubli. La clé contient l'identifiant de la version publiée : publier écrit
 * une nouvelle clé, et l'ancienne s'éteint toute seule. L'invalidation, qui est
 * la partie où l'on se trompe, n'existe pas comme problème.
 */
public interface PortCache {

    Optional<String> lire(String cle);

    void ecrire(String cle, String valeur, Duration duree);

    Statistiques statistiques();

    /** De quoi répondre à « le cache sert-il à quelque chose ? » sans deviner. */
    record Statistiques(long succes, long echecs, long evictions, int entrees) {

        public double tauxDeSucces() {
            long total = succes + echecs;
            return total == 0 ? 0 : (double) succes / total;
        }
    }
}
