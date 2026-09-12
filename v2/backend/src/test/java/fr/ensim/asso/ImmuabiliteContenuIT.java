package fr.ensim.asso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * L'archive est-elle réellement immuable ?
 *
 * <p>Toute la promesse produit — « /assos/bde/2023-2024 montre encore le site
 * de 2023-2024 » — repose sur le fait qu'une version publiée ne peut plus
 * changer. Dans la première version de cette conception, cette garantie
 * reposait sur une convention : « le code ne fait pas d'UPDATE ». Ces tests
 * vérifient qu'elle est désormais portée par la base.
 */
class ImmuabiliteContenuIT extends BaseIT {

    private UUID mandat;
    private UUID page;

    @BeforeEach
    void jeuDEssai() {
        jdbc.update("DELETE FROM media_usage");
        jdbc.update("DELETE FROM bloc");
        jdbc.update("DELETE FROM page_version");
        jdbc.update("DELETE FROM page");
        jdbc.update("DELETE FROM mandat");
        jdbc.update("DELETE FROM association");
        jdbc.update("DELETE FROM annee_universitaire");

        UUID asso = UUID.randomUUID();
        jdbc.update("INSERT INTO association (id, slug, nom, type_asso) VALUES (?,?,?,?)",
                asso, "bds", "Bureau des Sports", "BUREAU");
        jdbc.update("INSERT INTO annee_universitaire (code, debut, fin) VALUES (?,?,?)",
                "2025-2026", java.sql.Date.valueOf("2025-09-01"), java.sql.Date.valueOf("2026-08-31"));

        mandat = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO mandat (id, association_id, annee_code, debut_le, statut)
                VALUES (?,?,?,now(),'EN_FONCTION')
                """, mandat, asso, "2025-2026");

        page = UUID.randomUUID();
        jdbc.update("INSERT INTO page (id, mandat_id, slug, titre, ordre_menu) VALUES (?,?,?,?,0)",
                page, mandat, "accueil", "Accueil");
    }

    private UUID version(int numero, String statut) {
        UUID id = UUID.randomUUID();
        if ("BROUILLON".equals(statut)) {
            jdbc.update("""
                    INSERT INTO page_version (id, page_id, numero, statut, cree_par)
                    VALUES (?,?,?,'BROUILLON',?)
                    """, id, page, numero, UUID.randomUUID());
        } else {
            jdbc.update("""
                    INSERT INTO page_version (id, page_id, numero, statut, cree_par, publie_par, publie_le)
                    VALUES (?,?,?,?,?,?,now())
                    """, id, page, numero, statut, UUID.randomUUID(), UUID.randomUUID());
        }
        return id;
    }

    private UUID bloc(UUID version, int ordre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bloc (id, page_version_id, ordre, type, schema_version, payload)
                VALUES (?,?,?,'RICH_TEXT',1,'{"doc":{}}'::jsonb)
                """, id, version, ordre);
        return id;
    }

    @Test
    @DisplayName("au plus une version publiée par page")
    void uneSeuleVersionPubliee() {
        version(1, "PUBLIEE");

        assertThatThrownBy(() -> version(2, "PUBLIEE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("page_version_une_seule_publiee");
    }

    @Test
    @DisplayName("les blocs d'une version publiée ne peuvent plus être modifiés")
    void blocsFigesApresPublication() {
        UUID brouillon = version(1, "BROUILLON");
        UUID bloc = bloc(brouillon, 0);

        // Tant que c'est un brouillon, on modifie librement.
        assertThatCode(() -> jdbc.update(
                "UPDATE bloc SET payload = '{\"doc\":{\"v\":2}}'::jsonb WHERE id = ?", bloc))
                .doesNotThrowAnyException();

        jdbc.update("UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par=? WHERE id=?",
                UUID.randomUUID(), brouillon);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE bloc SET payload = '{\"doc\":{\"v\":3}}'::jsonb WHERE id = ?", bloc))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bloc figé");
    }

    @Test
    @DisplayName("on ne peut ni supprimer ni ajouter un bloc dans une version publiée")
    void ajoutEtSuppressionRefuses() {
        UUID publiee = version(1, "PUBLIEE");

        assertThatThrownBy(() -> bloc(publiee, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bloc figé");

        UUID brouillon = version(2, "BROUILLON");
        UUID b = bloc(brouillon, 0);
        jdbc.update("UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par=? WHERE id=?",
                UUID.randomUUID(), brouillon);
        // (la version 1 reste PUBLIEE -> l'index partiel interdirait ; on la range d'abord)

        assertThatThrownBy(() -> jdbc.update("DELETE FROM bloc WHERE id = ?", b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dépublier est interdit : sinon l'histoire redevient modifiable")
    void depublicationInterdite() {
        UUID publiee = version(1, "PUBLIEE");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE page_version SET statut='BROUILLON' WHERE id = ?", publiee))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transition de version interdite");
    }

    @Test
    @DisplayName("la seule transition autorisée après publication est l'archivage")
    void archivageAutorise() {
        UUID publiee = version(1, "PUBLIEE");

        assertThatCode(() -> jdbc.update(
                "UPDATE page_version SET statut='ARCHIVEE' WHERE id = ?", publiee))
                .doesNotThrowAnyException();

        // Et une version archivée reste figée.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE page_version SET statut='PUBLIEE' WHERE id = ?", publiee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un bloc référence une version de schéma qui existe toujours")
    void schemaVersionneEtReference() {
        UUID brouillon = version(1, "BROUILLON");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO bloc (id, page_version_id, ordre, type, schema_version, payload)
                VALUES (?,?,0,'RICH_TEXT',99,'{}'::jsonb)
                """, UUID.randomUUID(), brouillon))
                .as("un schéma inexistant doit être refusé : sinon un bloc archivé "
                  + "deviendrait invalidable des années plus tard")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bloc_type_versionne");
    }

    @Test
    @DisplayName("le registre des types de blocs est bien peuplé par les migrations")
    void registrePeuple() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM type_bloc", Integer.class);
        assertThat(n).isGreaterThanOrEqualTo(10);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM type_bloc WHERE type = 'RICH_TEXT'", Integer.class))
                .isEqualTo(1);
    }
}
