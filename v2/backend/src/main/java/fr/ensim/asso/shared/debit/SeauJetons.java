package fr.ensim.asso.shared.debit;

import java.time.Clock;
import java.time.Duration;

/**
 * Un seau à jetons : N requêtes par période, avec des jetons qui se
 * reconstituent progressivement plutôt que d'un coup.
 *
 * <p>La reconstitution continue évite l'effet de bord des fenêtres fixes, où
 * l'on peut envoyer 2N requêtes à cheval sur la frontière de deux fenêtres et
 * passer entre les mailles.
 */
final class SeauJetons {

    private final double capacite;
    private final double parNanoseconde;
    private final Clock horloge;

    private double jetons;
    private long dernierAppel;

    SeauJetons(int capacite, Duration periode, Clock horloge) {
        this.capacite = capacite;
        this.parNanoseconde = capacite / (double) periode.toNanos();
        this.horloge = horloge;
        this.jetons = capacite;
        this.dernierAppel = horloge.instant().getEpochSecond() * 1_000_000_000L
                          + horloge.instant().getNano();
    }

    /** Consomme un jeton. Rend {@code false} si le seau est vide. */
    synchronized boolean consommer() {
        long maintenant = horloge.instant().getEpochSecond() * 1_000_000_000L
                        + horloge.instant().getNano();
        long ecoule = Math.max(0, maintenant - dernierAppel);
        dernierAppel = maintenant;

        jetons = Math.min(capacite, jetons + ecoule * parNanoseconde);
        if (jetons < 1.0) {
            return false;
        }
        jetons -= 1.0;
        return true;
    }

    /** Secondes à attendre avant qu'un jeton soit disponible, au moins 1. */
    synchronized long attenteSecondes() {
        if (jetons >= 1.0) {
            return 0;
        }
        double manquant = 1.0 - jetons;
        return Math.max(1, (long) Math.ceil(manquant / parNanoseconde / 1_000_000_000L));
    }
}
