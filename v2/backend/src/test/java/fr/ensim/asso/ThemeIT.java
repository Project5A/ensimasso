package fr.ensim.asso;

import fr.ensim.asso.contenu.app.ServiceTheme;
import fr.ensim.asso.gouvernance.domain.Poste;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le thème d'un mandat, de bout en bout.
 *
 * <p>Tout existait sauf le service : aucune route, aucune méthode ne créait, ne
 * modifiait ni ne publiait un thème. {@code publier()} n'était appelée de nulle
 * part et {@code THEME_EDITER} n'était consultée nulle part. Un thème publié ne
 * pouvait donc naître que d'un INSERT à la main.
 */
class ThemeIT extends BaseIT {

    @Autowired
    private ServiceTheme themes;

    private UUID asso;
    private UUID mandat;
    private UUID president;
    private UUID etranger;

    @BeforeEach
    void jeuDEssai() {
        viderLesTables();
        asso = UUID.randomUUID();
        mandat = UUID.randomUUID();
        president = UUID.randomUUID();
        etranger = UUID.randomUUID();

        jdbc.update("INSERT INTO association (id, slug, nom, type_asso) VALUES (?,?,?,?)",
                asso, "bde", "Bureau des Élèves", "BUREAU");
        jdbc.update("INSERT INTO annee_universitaire (code, debut, fin) VALUES (?,?,?)",
                "2025-2026", java.sql.Date.valueOf("2025-09-01"), java.sql.Date.valueOf("2026-08-31"));
        jdbc.update("""
                INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut)
                VALUES (?,?,?,?::timestamptz,?::timestamptz,'EN_FONCTION')
                """, mandat, asso, "2025-2026", "2025-09-01T00:00:00Z", "2026-08-31T23:59:59Z");
        jdbc.update("""
                INSERT INTO membre_bureau (id, mandat_id, personne_id, poste, ordre)
                VALUES (?,?,?,?,0)
                """, UUID.randomUUID(), mandat, president, Poste.PRESIDENT.name());
    }

    @Test
    @DisplayName("un thème se crée, s'enregistre et se publie — ce qu'aucune route ne permettait")
    void cycleComplet() {
        assertThat(themes.enVigueur(president, mandat))
                .as("aucun thème au départ").isEmpty();

        var brouillon = themes.enregistrerBrouillon(president, mandat,
                "{\"couleurPrimaire\":\"#8B1E3F\"}");
        assertThat(brouillon.getStatut().name()).isEqualTo("BROUILLON");
        assertThat(themes.enVigueur(president, mandat))
                .as("un brouillon n'est pas en vigueur").isEmpty();

        var publie = themes.publier(president, mandat);
        assertThat(publie.getId()).isEqualTo(brouillon.getId());
        assertThat(themes.enVigueur(president, mandat))
                .hasValueSatisfying(t -> assertThat(t.getTokens()).contains("8B1E3F"));
    }

    @Test
    @DisplayName("publier une seconde fois archive le thème précédent")
    void publicationSuccessive() {
        themes.enregistrerBrouillon(president, mandat, "{\"couleurPrimaire\":\"#111111\"}");
        var premier = themes.publier(president, mandat);

        themes.enregistrerBrouillon(president, mandat, "{\"couleurPrimaire\":\"#222222\"}");
        var second = themes.publier(president, mandat);

        assertThat(second.getId()).isNotEqualTo(premier.getId());
        // L'index partiel theme_une_seule_publiee n'admet qu'une PUBLIEE par
        // mandat : sans archivage préalable, cette publication échouerait.
        assertThat(jdbc.queryForObject(
                "SELECT statut FROM theme_version WHERE id=?", String.class, premier.getId()))
                .isEqualTo("ARCHIVEE");
        assertThat(themes.enVigueur(president, mandat))
                .hasValueSatisfying(t -> assertThat(t.getTokens()).contains("222222"));
    }

    @Test
    @DisplayName("un thème publié ne se modifie plus : c'est ce qui rend l'archive fiable")
    void themePublieFige() {
        themes.enregistrerBrouillon(president, mandat, "{\"couleurPrimaire\":\"#111111\"}");
        var publie = themes.publier(president, mandat);

        assertThatThrownBy(() -> publie.remplacerTokens("{\"couleurPrimaire\":\"#999999\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne se modifie plus");
    }

    @Test
    @DisplayName("publier sans brouillon est refusé plutôt que silencieux")
    void publierSansBrouillon() {
        assertThatThrownBy(() -> themes.publier(president, mandat))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("aucun brouillon");
    }

    @Test
    @DisplayName("le thème d'un mandat n'est ni lisible ni modifiable par un étranger")
    void etrangerRefuse() {
        themes.enregistrerBrouillon(president, mandat, "{\"couleurPrimaire\":\"#111111\"}");

        assertThatThrownBy(() -> themes.aEditer(etranger, mandat))
                .isInstanceOf(Erreurs.AccesRefuse.class);
        assertThatThrownBy(() -> themes.enregistrerBrouillon(etranger, mandat, "{}"))
                .isInstanceOf(Erreurs.AccesRefuse.class);
        assertThatThrownBy(() -> themes.publier(etranger, mandat))
                .isInstanceOf(Erreurs.AccesRefuse.class);
    }
}
