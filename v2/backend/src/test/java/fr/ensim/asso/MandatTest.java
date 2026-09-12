package fr.ensim.asso;

import fr.ensim.asso.gouvernance.domain.AnneeUniversitaire;
import fr.ensim.asso.gouvernance.domain.Mandat;
import fr.ensim.asso.gouvernance.domain.StatutMandat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le cycle de vie d'un mandat — la correction centrale issue de la revue.
 *
 * <p>Le scénario déterminant est celui d'une passation de printemps : un bureau
 * élu à l'AG d'avril. Un modèle clé sur (association, année) ne peut pas le
 * représenter ; un modèle par période le représente sans cas particulier.
 */
class MandatTest {

    private static final UUID ASSO = UUID.randomUUID();

    private static OffsetDateTime le(int a, int m, int j) {
        return OffsetDateTime.of(a, m, j, 18, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("un mandat créé par la passation est en préparation et invisible")
    void naissanceEnPreparation() {
        Mandat m = Mandat.enPreparation(ASSO, "2026-2027", le(2026, 4, 12), le(2027, 4, 11));

        assertThat(m.getStatut()).isEqualTo(StatutMandat.PREPARATION);
        assertThat(m.estEnFonctionA(le(2026, 5, 1))).isFalse();
        assertThat(m.accepteEcriture())
                .as("le bureau entrant doit pouvoir préparer son site")
                .isTrue();
    }

    @Test
    @DisplayName("une AG d'avril investit le bureau — sans attendre septembre")
    void investitureAuPrintemps() {
        Mandat m = Mandat.enPreparation(ASSO, "2026-2027", le(2026, 4, 12), le(2027, 4, 11));

        m.investir(le(2026, 4, 12));

        assertThat(m.getStatut()).isEqualTo(StatutMandat.EN_FONCTION);
        assertThat(m.estEnFonctionA(le(2026, 4, 13))).isTrue();
        assertThat(m.estEnFonctionA(le(2026, 9, 1)))
                .as("le même bureau est toujours en fonction à la rentrée")
                .isTrue();
        assertThat(m.estEnFonctionA(le(2026, 4, 11)))
                .as("il n'était pas en fonction la veille de l'AG")
                .isFalse();
    }

    @Test
    @DisplayName("clore un mandat le rend immuable : c'est ce qui fonde l'archive")
    void clotureFigeLeMandat() {
        Mandat m = Mandat.enPreparation(ASSO, "2025-2026", le(2025, 4, 10), null);
        m.investir(le(2025, 4, 10));

        m.clore(le(2026, 4, 12));

        assertThat(m.getStatut()).isEqualTo(StatutMandat.CLOS);
        assertThat(m.accepteEcriture())
                .as("un mandat clos n'accepte plus aucune écriture")
                .isFalse();
        assertThat(m.estEnFonctionA(le(2026, 6, 1))).isFalse();
    }

    @Test
    @DisplayName("les transitions illégales sont refusées")
    void transitionsInterdites() {
        Mandat enPrepa = Mandat.enPreparation(ASSO, "2026-2027", le(2026, 4, 12), null);

        assertThatThrownBy(() -> enPrepa.clore(le(2026, 5, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en fonction");

        enPrepa.investir(le(2026, 4, 12));
        assertThatThrownBy(() -> enPrepa.investir(le(2026, 4, 13)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("préparation");
    }

    @Test
    @DisplayName("l'année suivante s'incrémente correctement")
    void calculAnneeSuivante() {
        assertThat(AnneeUniversitaire.anneeSuivante("2025-2026")).isEqualTo("2026-2027");
        assertThat(AnneeUniversitaire.anneeSuivante("2029-2030")).isEqualTo("2030-2031");
    }

    @Test
    @DisplayName("une année universitaire couvre septembre à août")
    void couvertureAnnee() {
        AnneeUniversitaire a = new AnneeUniversitaire("2025-2026",
                java.time.LocalDate.of(2025, 9, 1), java.time.LocalDate.of(2026, 8, 31));

        assertThat(a.contient(java.time.LocalDate.of(2025, 12, 25))).isTrue();
        assertThat(a.contient(java.time.LocalDate.of(2026, 7, 15)))
                .as("une adhésion early bird de juillet couvre encore cette année-là")
                .isTrue();
        assertThat(a.contient(java.time.LocalDate.of(2026, 9, 1))).isFalse();
    }
}
