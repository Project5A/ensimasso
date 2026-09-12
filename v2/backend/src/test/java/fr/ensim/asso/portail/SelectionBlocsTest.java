package fr.ensim.asso.portail;

import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.partenariat.domain.NiveauPartenaire;
import fr.ensim.asso.partenariat.domain.Partenaire;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La règle temporelle du portail : ce qu'un bloc retient parmi les données de
 * son mandat. C'est le genre de logique qui se trompe sans jamais lever
 * d'exception — un agenda vide ressemble à un agenda vide.
 */
class SelectionBlocsTest {

    private static final UUID MANDAT = UUID.randomUUID();
    private static final OffsetDateTime MAINTENANT =
            OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private Evenement ev(String slug, OffsetDateTime debut) {
        return new Evenement(MANDAT, slug, slug, debut);
    }

    private List<Evenement> troisEvenements() {
        return List.of(
                ev("vieux", MAINTENANT.minusMonths(2)),
                ev("hier", MAINTENANT.minusDays(1)),
                ev("demain", MAINTENANT.plusDays(1)));
    }

    @Test
    @DisplayName("sur une page en cours, « à venir » ne garde que le futur, dans l'ordre")
    void aVenirSurPageCourante() {
        List<Evenement> retenus = SelectionBlocs.agenda(
                Map.of("filtre", "A_VENIR"), troisEvenements(), true, MAINTENANT);

        assertThat(retenus).extracting(Evenement::getSlug).containsExactly("demain");
    }

    @Test
    @DisplayName("« à venir » est le défaut quand le bloc ne précise rien")
    void filtreParDefaut() {
        List<Evenement> retenus = SelectionBlocs.agenda(
                Map.of(), troisEvenements(), true, MAINTENANT);

        assertThat(retenus).extracting(Evenement::getSlug).containsExactly("demain");
    }

    @Test
    @DisplayName("sur une archive, « à venir » montre tout le mandat — sinon l'agenda serait vide")
    void archiveMontreToutLeMandat() {
        List<Evenement> retenus = SelectionBlocs.agenda(
                Map.of("filtre", "A_VENIR"), troisEvenements(), false, MAINTENANT);

        // Un mandat terminé n'a rien « à venir ». Appliquer le filtre à la
        // lettre donnerait une page d'archive laissant croire que ce bureau
        // n'a jamais rien organisé.
        assertThat(retenus).extracting(Evenement::getSlug)
                .containsExactly("demain", "hier", "vieux");
    }

    @Test
    @DisplayName("« passés » remonte du plus récent au plus ancien")
    void passesAntichronologiques() {
        List<Evenement> retenus = SelectionBlocs.agenda(
                Map.of("filtre", "PASSES"), troisEvenements(), true, MAINTENANT);

        assertThat(retenus).extracting(Evenement::getSlug).containsExactly("hier", "vieux");
    }

    @Test
    @DisplayName("la limite du payload est appliquée, et bornée comme le JSON Schema")
    void limiteBornee() {
        List<Evenement> beaucoup = java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> ev("e" + i, MAINTENANT.plusDays(i + 1L)))
                .toList();

        assertThat(SelectionBlocs.agenda(Map.of("limite", 3), beaucoup, true, MAINTENANT))
                .hasSize(3);
        assertThat(SelectionBlocs.agenda(Map.of(), beaucoup, true, MAINTENANT))
                .as("le défaut est 6").hasSize(6);
        assertThat(SelectionBlocs.agenda(Map.of("limite", 9999), beaucoup, true, MAINTENANT))
                .as("une limite hors bornes retombe sur le défaut, elle n'ouvre pas les vannes")
                .hasSize(6);
        assertThat(SelectionBlocs.agenda(Map.of("limite", 0), beaucoup, true, MAINTENANT))
                .hasSize(6);
    }

    @Test
    @DisplayName("un évènement en cours n'est pas encore passé")
    void evenementEnCours() {
        Evenement enCours = new Evenement(MANDAT, "soiree", "Soirée", MAINTENANT.minusHours(2));
        enCours.decrire("Soirée", null, null, null,
                MAINTENANT.minusHours(2), MAINTENANT.plusHours(3), null, null, false);

        assertThat(SelectionBlocs.agenda(Map.of("filtre", "A_VENIR"),
                List.of(enCours), true, MAINTENANT))
                .as("une soirée commencée à 10 h et finissant à 15 h est encore à l'affiche à midi")
                .hasSize(1);
    }

    // ------------------------------------------------------------ partenaires

    private Partenaire pa(String nom, NiveauPartenaire niveau) {
        return new Partenaire(MANDAT, nom, niveau);
    }

    @Test
    @DisplayName("sans niveaux demandés, un bloc partenaires les prend tous")
    void tousLesNiveaux() {
        List<Partenaire> tous = List.of(pa("A", NiveauPartenaire.OR), pa("B", NiveauPartenaire.SOUTIEN));

        assertThat(SelectionBlocs.partenaires(Map.of(), tous)).hasSize(2);
        assertThat(SelectionBlocs.partenaires(Map.of("niveaux", List.of()), tous)).hasSize(2);
    }

    @Test
    @DisplayName("les niveaux demandés filtrent, et un niveau inconnu ne fait pas tout passer")
    void filtreParNiveau() {
        List<Partenaire> tous = List.of(
                pa("Or", NiveauPartenaire.OR),
                pa("Argent", NiveauPartenaire.ARGENT),
                pa("Soutien", NiveauPartenaire.SOUTIEN));

        assertThat(SelectionBlocs.partenaires(Map.of("niveaux", List.of("OR", "ARGENT")), tous))
                .extracting(Partenaire::getNom).containsExactly("Or", "Argent");
        assertThat(SelectionBlocs.partenaires(Map.of("niveaux", List.of("PLATINE")), tous))
                .isEmpty();
    }
}
