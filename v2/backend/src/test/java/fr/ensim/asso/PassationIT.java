package fr.ensim.asso;

import fr.ensim.asso.gouvernance.domain.Passation;
import fr.ensim.asso.gouvernance.domain.MembreBureau;
import fr.ensim.asso.gouvernance.domain.MembreBureauRepository;
import fr.ensim.asso.gouvernance.domain.Poste;
import fr.ensim.asso.passation.ServicePassation;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La passation, traversée par ses vrais services.
 *
 * <p>Aucun test ne passait par {@link ServicePassation} : les tests unitaires
 * du domaine vérifient la machine à états en mémoire, et les tests
 * d'intégration existants attaquent la base en JDBC brut. Entre les deux,
 * personne ne vérifiait que la séquence tient réellement une fois les deux
 * bouts assemblés — et c'est exactement là que {@code annuler()} échouait à
 * tous les coups sur une violation de clé étrangère.
 */
class PassationIT extends BaseIT {

    @Autowired
    private ServicePassation passations;

    @Autowired
    private MembreBureauRepository membres;

    @Autowired
    private fr.ensim.asso.gouvernance.app.PolitiqueAcces politique;

    private UUID asso;
    private UUID president;
    private UUID mandatSortant;

    @BeforeEach
    void jeuDEssai() {
        viderLesTables();

        asso = UUID.randomUUID();
        president = UUID.randomUUID();
        mandatSortant = UUID.randomUUID();

        jdbc.update("INSERT INTO association (id, slug, nom, type_asso) VALUES (?,?,?,?)",
                asso, "bde", "Bureau des Élèves", "BUREAU");
        for (int a = 2024; a <= 2028; a++) {
            jdbc.update("INSERT INTO annee_universitaire (code, debut, fin) VALUES (?,?,?)",
                    a + "-" + (a + 1), java.sql.Date.valueOf(a + "-09-01"),
                    java.sql.Date.valueOf((a + 1) + "-08-31"));
        }
        jdbc.update("""
                INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut)
                VALUES (?,?,?,?::timestamptz,?::timestamptz,'EN_FONCTION')
                """, mandatSortant, asso, "2025-2026",
                "2025-09-01T00:00:00Z", "2026-08-31T23:59:59Z");
        jdbc.update("""
                INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre)
                VALUES (?,?,?,'PRESIDENT',0)
                """, UUID.randomUUID(), mandatSortant, president);
    }

    private Passation preparer() {
        return passations.preparer(president, asso,
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2027-08-31T23:59:59Z"));
    }

    @Test
    @DisplayName("annuler une passation préparée ne lève pas, et détache le mandat entrant")
    void annulationPossible() {
        Passation p = preparer();
        UUID entrant = p.getMandatEntrantId();
        assertThat(entrant).isNotNull();

        // Le défaut : la ligne passation référençait encore le mandat entrant,
        // et la clé étrangère était en NO ACTION. Le DELETE violait
        // passation_mandat_entrant_id_fkey — à CHAQUE appel, sans exception.
        assertThatCode(() -> passations.annuler(president, p.getId()))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                "SELECT statut FROM passation WHERE id=?", String.class, p.getId()))
                .isEqualTo("ANNULEE");
        assertThat(jdbc.queryForObject(
                "SELECT mandat_entrant_id IS NULL FROM passation WHERE id=?",
                Boolean.class, p.getId()))
                .as("la trace de la passation survit, détachée de son mandat")
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mandat WHERE id=?", Integer.class, entrant))
                .as("le mandat entrant disparaît : sinon mandat_un_seul_par_annee "
                  + "interdirait pour toujours de repréparer cette année-là")
                .isZero();
    }

    @Test
    @DisplayName("après annulation, l'association peut repréparer la même année")
    void repreparationPossible() {
        Passation premiere = preparer();
        passations.annuler(president, premiere.getId());

        Passation seconde = preparer();

        assertThat(seconde.getId()).isNotEqualTo(premiere.getId());
        assertThat(seconde.getMandatEntrantId()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM passation WHERE association_id=?", Integer.class, asso))
                .as("les deux passations coexistent : l'annulée pour mémoire, "
                  + "la nouvelle pour de bon")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("on ne désigne plus personne dans une passation annulée")
    void designationRefuseeApresAnnulation() {
        Passation p = preparer();
        passations.annuler(president, p.getId());

        assertThatThrownBy(() -> passations.designer(
                    president, p.getId(), UUID.randomUUID(), Poste.PRESIDENT, 0))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("ANNULEE");
    }

    @Test
    @DisplayName("on ne désigne plus personne dans une passation déjà activée")
    void designationRefuseeApresActivation() {
        Passation p = preparer();
        UUID entrantPresident = UUID.randomUUID();
        passations.designer(president, p.getId(), entrantPresident, Poste.PRESIDENT, 0);
        passations.designer(president, p.getId(), UUID.randomUUID(), Poste.TRESORIER, 1);
        passations.marquerBureauComplete(entrantPresident, p.getId());
        passations.activer(president, p.getId(), OffsetDateTime.parse("2026-09-15T18:00:00Z"));

        // Sans garde, cette route servait aussi à ajouter des membres à un
        // bureau déjà investi, hors de toute règle de gouvernance.
        assertThatThrownBy(() -> passations.designer(
                    entrantPresident, p.getId(), UUID.randomUUID(), Poste.SECRETAIRE, 2))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("ACTIVEE");
    }

    @Test
    @DisplayName("le bureau ENTRANT voit son mandat : c'est le seul chemin qui y mène")
    void leBureauEntrantAUneEntree() {
        Passation p = preparer();
        UUID entrantPresident = UUID.randomUUID();
        passations.designer(president, p.getId(), entrantPresident, Poste.PRESIDENT, 0);

        // postesActifsDe est la SEULE requête dont le tableau de bord se sert
        // pour savoir où l'utilisateur peut aller. Elle filtrait EN_FONCTION :
        // le président tout juste désigné lisait « Aucun mandat en cours », et
        // en dessous qu'il devait se faire désigner — ce qui venait d'être fait.
        assertThat(membres.postesActifsDe(entrantPresident))
                .as("désigné dans la passation, il doit pouvoir préparer son site")
                .extracting(MembreBureau::getMandatId)
                .containsExactly(p.getMandatEntrantId());
    }

    @Test
    @DisplayName("l'autorisation et la navigation répondent la même chose sur le mandat entrant")
    void navigationEtAutorisationDAccord() {
        Passation p = preparer();
        UUID entrantPresident = UUID.randomUUID();
        passations.designer(president, p.getId(), entrantPresident, Poste.PRESIDENT, 0);

        // PolitiqueAcces autorise l'écriture sur un mandat en PRÉPARATION — son
        // commentaire le dit en toutes lettres. La liste, elle, l'ignorait :
        // deux règles qui répondent à la même question, et qui divergeaient.
        assertThat(politique.peutSurMandat(entrantPresident,
                        fr.ensim.asso.gouvernance.domain.Permission.PAGE_EDITER,
                        p.getMandatEntrantId()))
                .isTrue();
        assertThat(membres.postesActifsDe(entrantPresident)).isNotEmpty();
    }

    @Test
    @DisplayName("un mandat CLOS ne figure plus dans la liste : on n'y écrit plus")
    void mandatClosAbsentDeLaListe() {
        Passation p = preparer();
        UUID entrantPresident = UUID.randomUUID();
        passations.designer(president, p.getId(), entrantPresident, Poste.PRESIDENT, 0);
        passations.designer(president, p.getId(), UUID.randomUUID(), Poste.TRESORIER, 1);
        passations.marquerBureauComplete(entrantPresident, p.getId());
        passations.activer(president, p.getId(), OffsetDateTime.parse("2026-09-15T18:00:00Z"));

        // L'activation clôt le mandat sortant. Son président n'a plus rien à y
        // écrire — le proposer serait un lien vers un refus.
        assertThat(membres.postesActifsDe(president))
                .as("le mandat sortant est CLOS après la passation")
                .isEmpty();
        assertThat(membres.postesActifsDe(entrantPresident))
                .extracting(MembreBureau::getMandatId)
                .containsExactly(p.getMandatEntrantId());
    }
}
