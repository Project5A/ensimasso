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
 *
 * <p>Elle ne l'était qu'à moitié : la garantie tenait contre la MODIFICATION et
 * pas contre l'EFFACEMENT. bloc → page_version → page → mandat est une chaîne
 * d'ON DELETE CASCADE, et dans une cascade la ligne parente est déjà supprimée
 * quand le trigger de l'enfant s'exécute — le SELECT de {@code bloc_fige} ne
 * trouvait alors plus la version et laissait passer. Supprimer la page suffisait
 * à effacer l'archive. V13 ferme chaque porte à son étage ; les cas ci-dessous
 * les essaient une par une.
 */
class ImmuabiliteContenuIT extends BaseIT {

    private UUID mandat;
    private UUID page;

    @BeforeEach
    void jeuDEssai() {
        viderLesTables();

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

        // La version 1 doit être ARCHIVEE avant que la 2 soit publiée :
        // l'index partiel page_version_une_seule_publiee n'admet qu'une seule
        // version PUBLIEE par page. Ce n'est pas un contournement, c'est la
        // transition que le domaine impose, et la seule que le trigger accepte.
        //
        // Cette ligne manquait : le commentaire qui annonçait l'archivage était
        // écrit, l'archivage non. Le défaut est resté invisible parce que ce cas
        // n'a jamais pu s'exécuter — il mourait avant, dans un nettoyage cassé.
        jdbc.update("UPDATE page_version SET statut='ARCHIVEE' WHERE id=?", publiee);
        jdbc.update("UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par=? WHERE id=?",
                UUID.randomUUID(), brouillon);

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

    // ------------------------------------------- effacer, plutôt que modifier

    /** Une version publiée portant un bloc, construite comme le fait le code. */
    private UUID versionPublieeAvecBloc(int numero) {
        UUID v = version(numero, "BROUILLON");
        bloc(v, 0);
        jdbc.update("UPDATE page_version SET statut='PUBLIEE', publie_le=now(), "
                  + "publie_par=? WHERE id=?", UUID.randomUUID(), v);
        return v;
    }

    @Test
    @DisplayName("une version publiée ne s'efface pas : c'est l'archive")
    void versionPublieeNonSupprimable() {
        UUID v = versionPublieeAvecBloc(1);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM page_version WHERE id = ?", v))
                .as("la modification était refusée, l'effacement passait — et efface "
                  + "le même contenu")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("c'est l'archive");
    }

    @Test
    @DisplayName("ni par le dessus : supprimer la page qui la porte est refusé aussi")
    void pagePortantUneVersionPublieeNonSupprimable() {
        versionPublieeAvecBloc(1);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM page WHERE id = ?", page))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("version publiée ou archivée");
    }

    @Test
    @DisplayName("ni par le dessus du dessus : un mandat qui a gouverné ne se supprime pas")
    void mandatEnFonctionNonSupprimable() {
        versionPublieeAvecBloc(1);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM mandat WHERE id = ?", mandat))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("porte une archive");
    }

    @Test
    @DisplayName("un brouillon, lui, s'abandonne : on n'a pas remplacé un défaut par une paralysie")
    void brouillonSupprimable() {
        UUID brouillon = version(1, "BROUILLON");
        bloc(brouillon, 0);

        assertThatCode(() -> jdbc.update("DELETE FROM page_version WHERE id = ?", brouillon))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------- le thème

    private UUID themePublie() {
        UUID t = UUID.randomUUID();
        jdbc.update("INSERT INTO theme_version (id, mandat_id, numero, statut, tokens) "
                  + "VALUES (?,?,1,'BROUILLON',?::jsonb)",
                t, mandat, "{\"couleurPrimaire\":\"#123456\"}");
        jdbc.update("UPDATE theme_version SET statut='PUBLIEE' WHERE id=?", t);
        return t;
    }

    @Test
    @DisplayName("un thème publié ne se réécrit plus — la règle vivait dans le code seul")
    void themePublieFige() {
        UUID t = themePublie();

        // L'entité Java porte la règle depuis toujours : « un thème PUBLIEE ne
        // se modifie plus ». La base, elle, acceptait l'UPDATE — exactement la
        // situation que V2 décrivait pour les pages avant son trigger.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE theme_version SET tokens = ?::jsonb WHERE id = ?",
                "{\"couleurPrimaire\":\"#ff0000\"}", t))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("thème figé");
    }

    @Test
    @DisplayName("et il ne se dépublie pas : sinon il redeviendrait modifiable")
    void themeNeSeDepubliePas() {
        UUID t = themePublie();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE theme_version SET statut='BROUILLON' WHERE id = ?", t))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transition de version interdite");
    }

    @Test
    @DisplayName("l'archiver reste possible, jetons inchangés : c'est ce que fait la publication")
    void themeArchivable() {
        UUID t = themePublie();

        // Hibernate réécrit TOUTES les colonnes à chaque sauvegarde, jetons
        // compris. Une garde qui refuserait « tout UPDATE » casserait donc la
        // publication elle-même.
        assertThatCode(() -> jdbc.update(
                "UPDATE theme_version SET statut='ARCHIVEE', tokens = ?::jsonb WHERE id = ?",
                "{\"couleurPrimaire\":\"#123456\"}", t))
                .doesNotThrowAnyException();
    }
}
