package fr.ensim.asso.shared.debit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
        this.dernierAppel = maintenantNanos();
    }

    /**
     * L'instant courant en nanosecondes, lu <strong>une seule fois</strong>.
     *
     * <p>Le calcul composait deux appels distincts à l'horloge :
     * {@code instant().getEpochSecond() * 1e9 + instant().getNano()}. Entre les
     * deux, l'horloge peut franchir une seconde : la seconde vient de la
     * première lecture, les nanosecondes de la seconde, et l'horodatage obtenu
     * recule alors d'une seconde entière. Au tour suivant, le temps écoulé est
     * surévalué d'autant et le seau est recrédité jusqu'à sa capacité — la
     * limite saute, une fois sur un milliard, sans laisser de trace.
     */
    private long maintenantNanos() {
        Instant t = horloge.instant();
        return t.getEpochSecond() * 1_000_000_000L + t.getNano();
    }

    /** Consomme un jeton. Rend {@code false} si le seau est vide. */
    synchronized boolean consommer() {
        long maintenant = maintenantNanos();
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
