package fr.ensim.asso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Ces tests vérifient que les garanties du modèle temporel sont réellement
 * portées par la BASE, et pas seulement par le code Java.
 *
 * <p>C'est la différence entre une promesse et une propriété. Le code peut
 * être contourné par un script de maintenance, une migration de données, ou un
 * futur développeur pressé ; une contrainte ne l'est pas.
 */
class ContraintesTemporellesIT extends BaseIT {

    private UUID asso;

    @BeforeEach
    void jeuDEssai() {
        jdbc.update("DELETE FROM passation");
        jdbc.update("DELETE FROM membre_bureau");
        jdbc.update("DELETE FROM mandat");
        jdbc.update("DELETE FROM association");
        jdbc.update("DELETE FROM annee_universitaire");

        asso = UUID.randomUUID();
        jdbc.update("INSERT INTO association (id, slug, nom, type_asso) VALUES (?,?,?,?)",
                asso, "bde", "Bureau des Élèves", "BUREAU");
        for (int a = 2024; a <= 2028; a++) {
            jdbc.update("INSERT INTO annee_universitaire (code, debut, fin) VALUES (?,?,?)",
                    a + "-" + (a + 1), java.sql.Date.valueOf(a + "-09-01"),
                    java.sql.Date.valueOf((a + 1) + "-08-31"));
        }
    }

    private void insererMandat(String annee, String debut, String fin, String statut) {
        jdbc.update("""
                INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut)
                VALUES (?,?,?,?::timestamptz,?::timestamptz,?)
                """, UUID.randomUUID(), asso, annee, debut, fin, statut);
    }

    @Test
    @DisplayName("deux mandats qui se chevauchent sont refusés par la base")
    void chevauchementRefuse() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", "2026-04-12T18:00:00Z", "EN_FONCTION");

        assertThatThrownBy(() ->
                insererMandat("2026-2027", "2026-01-01T18:00:00Z", "2027-04-11T18:00:00Z", "EN_FONCTION"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mandat_pas_de_chevauchement");
    }

    @Test
    @DisplayName("une passation de printemps est représentable : avril à avril, sans trou")
    void passationDePrintemps() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", "2026-04-12T18:00:00Z", "CLOS");

        // Le mandat suivant démarre exactement là où le précédent s'arrête.
        // tstzrange est semi-ouvert [début, fin) : il n'y a donc ni trou ni
        // chevauchement, et c'est précisément ce que le modèle par année
        // était incapable d'exprimer.
        assertThatCode(() ->
                insererMandat("2026-2027", "2026-04-12T18:00:00Z", null, "EN_FONCTION"))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mandat WHERE association_id = ?", Integer.class, asso))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("un mandat en préparation peut chevaucher : sa période est provisoire")
    void preparationToleree() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", null, "EN_FONCTION");

        assertThatCode(() ->
                insererMandat("2026-2027", "2026-04-12T18:00:00Z", "2027-04-11T18:00:00Z", "PREPARATION"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une association ne peut avoir qu'un seul mandat en fonction")
    void unSeulMandatEnFonction() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", "2026-04-09T18:00:00Z", "EN_FONCTION");

        // Périodes disjointes : l'exclusion GiST passerait. C'est l'index
        // unique partiel — la règle métier énoncée directement — qui refuse.
        assertThatThrownBy(() ->
                insererMandat("2026-2027", "2026-04-12T18:00:00Z", null, "EN_FONCTION"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mandat_un_seul_en_fonction");
    }

    @Test
    @DisplayName("la colonne période est générée : elle ne peut pas diverger des bornes")
    void periodeGeneree() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", "2026-04-12T18:00:00Z", "EN_FONCTION");

        Boolean contient = jdbc.queryForObject(
                "SELECT periode @> '2025-09-01T12:00:00Z'::timestamptz FROM mandat WHERE association_id = ?",
                Boolean.class, asso);
        assertThat(contient)
                .as("la rentrée de septembre tombe bien dans un mandat élu en avril")
                .isTrue();
    }

    @Test
    @DisplayName("les postes statutaires sont uniques dans un bureau")
    void unSeulPresident() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", null, "EN_FONCTION");
        UUID mandat = jdbc.queryForObject(
                "SELECT id FROM mandat WHERE association_id = ?", UUID.class, asso);

        jdbc.update("INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), mandat, UUID.randomUUID(), "PRESIDENT", 0);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), mandat, UUID.randomUUID(), "PRESIDENT", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("membre_postes_statutaires_uniques");
    }

    @Test
    @DisplayName("un membre révoqué libère son poste")
    void revocationLibereLePoste() {
        insererMandat("2025-2026", "2025-04-10T18:00:00Z", null, "EN_FONCTION");
        UUID mandat = jdbc.queryForObject(
                "SELECT id FROM mandat WHERE association_id = ?", UUID.class, asso);
        UUID premier = UUID.randomUUID();

        jdbc.update("INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre) VALUES (?,?,?,?,?)",
                premier, mandat, UUID.randomUUID(), "TRESORIER", 0);
        jdbc.update("UPDATE membre_bureau SET revoque_le = now() WHERE id = ?", premier);

        assertThatCode(() -> jdbc.update(
                "INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), mandat, UUID.randomUUID(), "TRESORIER", 0))
                .as("le trésorier démissionnaire est remplaçable en cours de mandat")
                .doesNotThrowAnyException();
    }
}
