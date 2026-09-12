package fr.ensim.asso;

import fr.ensim.asso.media.domain.MediaAsset;
import fr.ensim.asso.media.domain.StatutMedia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * La médiathèque, et surtout la garantie qui évite de refaire STOR-01.
 *
 * <p>Rappel du bug : la v1 générait une URL SAS valable 24 h et écrivait
 * l'URL complète, jeton compris, dans {@code User.photo} et
 * {@code Asso.gallery}. Rien ne la régénérait — toutes les images meurent un
 * jour après leur dépôt. Ce n'est pas un défaut de conception théorique :
 * c'est une perte de données silencieuse, en cours, sur le site actuel.
 */
class MediaTest {

    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID DEPOSANT = UUID.randomUUID();

    private static OffsetDateTime le(int a, int m, int j) {
        return OffsetDateTime.of(a, m, j, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private MediaAsset media() {
        return new MediaAsset(ASSO, "2025-2026", "bde/2025-2026/" + UUID.randomUUID() + ".jpg",
                "gala.jpg", "image/jpeg", DEPOSANT);
    }

    @ParameterizedTest(name = "refuse « {0} » comme clé")
    @ValueSource(strings = {
            "https://compte.blob.core.windows.net/userphotos/x.jpg?sv=2024&sig=abc",
            "http://localhost:9000/media/x.jpg",
            "bde/2025-2026/x.jpg?token=abc",
    })
    @DisplayName("une URL ne peut pas être utilisée comme clé d'objet")
    void refuseUneUrlCommeCle(String cleInvalide) {
        assertThatThrownBy(() ->
                new MediaAsset(ASSO, "2025-2026", cleInvalide, "x.jpg", "image/jpeg", DEPOSANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STOR-01");
    }

    @Test
    @DisplayName("une clé d'objet ordinaire est acceptée")
    void accepteUneCle() {
        assertThatCode(() -> new MediaAsset(ASSO, "2025-2026",
                "bde/2025-2026/9f1c.jpg", "gala.jpg", "image/jpeg", DEPOSANT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un média naît en attente : la clé est réservée, l'objet pas encore là")
    void naissanceEnAttente() {
        MediaAsset m = media();

        assertThat(m.getStatut()).isEqualTo(StatutMedia.ATTENTE_DEPOT);
        assertThat(m.estDisponible())
                .as("un média non confirmé ne doit jamais être servi dans une page")
                .isFalse();
        assertThat(m.getTailleOctets()).isNull();
    }

    @Test
    @DisplayName("la confirmation enregistre la taille et le type RÉELS, pas ceux déclarés")
    void confirmationRelitLeReel() {
        MediaAsset m = media();

        // Le client avait annoncé image/jpeg ; le stockage dit image/png.
        m.confirmer(482_113L, "image/png", le(2025, 10, 4));

        assertThat(m.estDisponible()).isTrue();
        assertThat(m.getTailleOctets()).isEqualTo(482_113L);
        assertThat(m.getContentType())
                .as("c'est le type relevé dans le stockage qui fait foi")
                .isEqualTo("image/png");
        assertThat(m.getConfirmeLe()).isEqualTo(le(2025, 10, 4));
    }

    @Test
    @DisplayName("on ne confirme pas deux fois")
    void doubleConfirmationRefusee() {
        MediaAsset m = media();
        m.confirmer(1000L, "image/jpeg", le(2025, 10, 4));

        assertThatThrownBy(() -> m.confirmer(2000L, "image/jpeg", le(2025, 10, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà");
    }

    @Test
    @DisplayName("un média rejeté n'est pas disponible")
    void rejetEmpecheLUsage() {
        MediaAsset m = media();
        m.rejeter();

        assertThat(m.getStatut()).isEqualTo(StatutMedia.REJETE);
        assertThat(m.estDisponible()).isFalse();
    }

    @Test
    @DisplayName("la suppression conserve la ligne : une archive doit rester explicable")
    void suppressionConserveLaTrace() {
        MediaAsset m = media();
        m.confirmer(1000L, "image/jpeg", le(2025, 10, 4));

        m.marquerSupprime();

        assertThat(m.getStatut()).isEqualTo(StatutMedia.SUPPRIME);
        assertThat(m.getCle())
                .as("la clé reste connue : sans elle, plus moyen d'expliquer un trou dans une archive")
                .isNotNull();
    }

    @Test
    @DisplayName("le média est rangé par association et par année")
    void rangementParMandat() {
        MediaAsset m = media();

        assertThat(m.getCle())
                .startsWith("bde/2025-2026/")
                .as("le préfixe rend triviale une politique de cycle de vie par année");
        assertThat(m.getAnneeCode()).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("le texte alternatif est modifiable : l'accessibilité n'est pas figée au dépôt")
    void descriptionModifiable() {
        MediaAsset m = media();
        m.decrire("Photo de groupe du gala 2025");

        assertThat(m.getTexteAlternatif()).isEqualTo("Photo de groupe du gala 2025");
    }
}
