package fr.ensim.asso.shared.debit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le seau à jetons et son horloge.
 *
 * <p>Dans le paquet du seau, qui n'est pas public : le sortir pour le tester
 * élargirait sa surface sans raison.
 */
class SeauJetonsTest {

    /** Horloge qui compte ses lectures et avance d'un tic à chaque fois. */
    private static final class HorlogeComptee extends Clock {
        private final AtomicInteger lectures = new AtomicInteger();
        private final List<Instant> suite;

        HorlogeComptee(List<Instant> suite) { this.suite = suite; }

        @Override public Instant instant() {
            int i = lectures.getAndIncrement();
            return suite.get(Math.min(i, suite.size() - 1));
        }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
    }

    @Test
    @DisplayName("l'horodatage du seau vient d'UNE seule lecture de l'horloge")
    void uneSeuleLectureDHorlogeParConsommation() {
        var horloge = new HorlogeComptee(List.of(Instant.parse("2026-01-01T00:00:00Z")));
        var seau = new SeauJetons(10, Duration.ofMinutes(1), horloge);

        int apresConstruction = horloge.lectures.get();
        seau.consommer();

        assertThat(horloge.lectures.get() - apresConstruction)
                .as("deux lectures composaient un horodatage — la seconde de "
                  + "l'une, les nanosecondes de l'autre")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("le seau se recrédite proportionnellement au temps écoulé, pas par bonds")
    void recreditProportionnel() {
        // Ce cas ne reproduit PAS le franchissement de seconde : le seau part
        // plein, et le crédit en trop était écrêté par la capacité. Écrit
        // comme une reproduction, il aurait été vert quel que soit le code —
        // c'est le cas ci-dessus, qui compte les lectures d'horloge, qui tient
        // la garantie. Celui-ci vérifie ce qu'il dit : le rythme du recrédit.
        var horloge = new HorlogeComptee(List.of(
                Instant.parse("2026-01-01T00:00:00Z"),   // construction
                Instant.parse("2026-01-01T00:00:00Z"),   // 1re consommation
                Instant.parse("2026-01-01T00:00:00Z"),   // 2e  consommation
                Instant.parse("2026-01-01T00:00:00Z"),   // 3e  : seau vide
                Instant.parse("2026-01-01T00:00:15Z"),   // +15 s sur 60 : 0,5 jeton
                Instant.parse("2026-01-01T00:00:30Z")));  // +30 s : un jeton entier

        var seau = new SeauJetons(2, Duration.ofMinutes(1), horloge);

        assertThat(seau.consommer()).as("1er jeton").isTrue();
        assertThat(seau.consommer()).as("2e jeton").isTrue();
        assertThat(seau.consommer()).as("seau vide").isFalse();
        assertThat(seau.consommer()).as("après 15 s, un demi-jeton ne suffit pas").isFalse();
        assertThat(seau.consommer()).as("après 30 s, un jeton entier est disponible").isTrue();
    }
}
