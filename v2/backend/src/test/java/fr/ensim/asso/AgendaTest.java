package fr.ensim.asso;

import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.agenda.domain.StatutEvenement;
import fr.ensim.asso.partenariat.domain.NiveauPartenaire;
import fr.ensim.asso.partenariat.domain.Partenaire;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles d'un évènement et d'un partenariat, hors de toute base. */
class AgendaTest {

    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 3, 1, 18, 0, 0, 0, ZoneOffset.UTC);

    private Evenement evenement(OffsetDateTime debut, OffsetDateTime fin) {
        Evenement e = new Evenement(UUID.randomUUID(), "gala", "Gala", debut);
        e.decrire("Gala", null, null, "Le Mans", debut, fin, null, null, false);
        return e;
    }

    @Test
    @DisplayName("un évènement d'une journée reste à l'agenda jusqu'à sa fin, pas jusqu'à son début")
    void finEffective() {
        Evenement journee = evenement(T0, T0.plusHours(6));
        assertThat(journee.estPasse(T0.plusHours(1)))
                .as("à 19 h, un évènement qui finit à minuit n'est pas passé")
                .isFalse();
        assertThat(journee.estPasse(T0.plusHours(7))).isTrue();
    }

    @Test
    @DisplayName("sans date de fin, l'évènement est passé dès que son début l'est")
    void sansFin() {
        Evenement ponctuel = evenement(T0, null);
        assertThat(ponctuel.finEffective()).isEqualTo(T0);
        assertThat(ponctuel.estPasse(T0.plusMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("une fin antérieure au début est refusée")
    void periodeOrdonnee() {
        Evenement e = evenement(T0, null);
        assertThatThrownBy(() -> e.decrire("Gala", null, null, null, T0, T0.minusHours(1),
                null, null, false))
                .isInstanceOf(Erreurs.RequeteInvalide.class);
    }

    @Test
    @DisplayName("un brouillon n'est pas public ; un évènement annulé l'est encore")
    void visibilitePublique() {
        Evenement e = evenement(T0, null);
        assertThat(e.getStatut()).isEqualTo(StatutEvenement.BROUILLON);
        assertThat(e.estPublic()).isFalse();

        e.publier();
        assertThat(e.estPublic()).isTrue();

        e.annuler("salle indisponible", T0);
        assertThat(e.estPublic())
                .as("un évènement annulé doit rester visible, barré : "
                    + "le faire disparaître laisse sans réponse ceux qui comptaient venir")
                .isTrue();
        assertThat(e.getMotifAnnulation()).isEqualTo("salle indisponible");
    }

    @Test
    @DisplayName("annuler deux fois ne change rien ; republier après annulation est refusé")
    void annulationIrreversible() {
        Evenement e = evenement(T0, null);
        e.publier();
        e.annuler("pluie", T0);
        e.annuler("autre motif", T0.plusDays(1));

        assertThat(e.getMotifAnnulation())
                .as("la seconde annulation ne doit pas réécrire le motif communiqué")
                .isEqualTo("pluie");
        assertThat(e.getAnnuleLe()).isEqualTo(T0);

        assertThatThrownBy(e::publier).isInstanceOf(Erreurs.Conflit.class);
    }

    @Test
    @DisplayName("les partenaires s'affichent par niveau, et OR passe avant ARGENT")
    void ordreDesNiveaux() {
        UUID mandat = UUID.randomUUID();
        List<Partenaire> liste = new ArrayList<>(List.of(
                new Partenaire(mandat, "Zêta", NiveauPartenaire.SOUTIEN),
                new Partenaire(mandat, "Alpha", NiveauPartenaire.ARGENT),
                new Partenaire(mandat, "Oméga", NiveauPartenaire.OR),
                new Partenaire(mandat, "Bêta", NiveauPartenaire.BRONZE)));
        liste.sort(Partenaire.AFFICHAGE);

        // Un tri SQL sur la colonne texte donnerait ARGENT, BRONZE, OR, SOUTIEN,
        // c'est-à-dire le partenaire principal en troisième position.
        assertThat(liste).extracting(Partenaire::getNom)
                .containsExactly("Oméga", "Alpha", "Bêta", "Zêta");
    }

    @Test
    @DisplayName("à niveau égal, le rang choisi par l'asso tranche, puis le nom")
    void ordreStable() {
        UUID mandat = UUID.randomUUID();
        Partenaire premier = new Partenaire(mandat, "Bravo", NiveauPartenaire.OR);
        premier.decrire("Bravo", NiveauPartenaire.OR, null, null, 1, true);
        Partenaire second = new Partenaire(mandat, "Alpha", NiveauPartenaire.OR);
        second.decrire("Alpha", NiveauPartenaire.OR, null, null, 2, true);
        Partenaire exAequo = new Partenaire(mandat, "Aardvark", NiveauPartenaire.OR);
        exAequo.decrire("Aardvark", NiveauPartenaire.OR, null, null, 1, true);

        List<Partenaire> liste = new ArrayList<>(List.of(second, premier, exAequo));
        liste.sort(Partenaire.AFFICHAGE);
        assertThat(liste).extracting(Partenaire::getNom)
                .containsExactly("Aardvark", "Bravo", "Alpha");
    }
}
