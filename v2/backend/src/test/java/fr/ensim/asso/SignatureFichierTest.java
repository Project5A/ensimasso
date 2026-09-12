package fr.ensim.asso;

import fr.ensim.asso.media.domain.SignatureFichier;
import fr.ensim.asso.media.domain.SignatureFichier.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La vérification qui manquait : ce qu'un fichier EST, pas ce qu'il dit être.
 *
 * <p>Le dépôt se fait en direct vers le stockage, avec un PUT signé dont le
 * navigateur choisit l'en-tête {@code Content-Type}. Relire ce type auprès de
 * S3 revient donc à relire le client. Ces tests décrivent exactement ce qui
 * passait avant : un document HTML porteur de script, annoncé {@code image/png}.
 */
class SignatureFichierTest {

    private static byte[] octets(int... valeurs) {
        byte[] b = new byte[valeurs.length];
        for (int i = 0; i < valeurs.length; i++) {
            b[i] = (byte) valeurs[i];
        }
        return b;
    }

    private static byte[] concat(byte[] tete, String suite) {
        var sortie = new ByteArrayOutputStream();
        sortie.writeBytes(tete);
        sortie.writeBytes(suite.getBytes(StandardCharsets.UTF_8));
        return sortie.toByteArray();
    }

    private static final byte[] PNG = octets(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13);
    private static final byte[] JPEG = octets(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F');
    private static final byte[] GIF = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = octets('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P');
    private static final byte[] AVIF = octets(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f');
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("les formats acceptés sont reconnus par leurs octets d'en-tête")
    void formatsReconnus() {
        assertThat(SignatureFichier.identifier(PNG)).isEqualTo(Type.PNG);
        assertThat(SignatureFichier.identifier(JPEG)).isEqualTo(Type.JPEG);
        assertThat(SignatureFichier.identifier(GIF)).isEqualTo(Type.GIF);
        assertThat(SignatureFichier.identifier(WEBP)).isEqualTo(Type.WEBP);
        assertThat(SignatureFichier.identifier(AVIF)).isEqualTo(Type.AVIF);
        assertThat(SignatureFichier.identifier(PDF)).isEqualTo(Type.PDF);
    }

    @ParameterizedTest(name = "« {0} » est identifié comme du HTML ou du script")
    @ValueSource(strings = {
            "<!DOCTYPE html><html><script>fetch('/api/…')</script>",
            "<html><body onload=\"alert(1)\">",
            "<script>document.cookie</script>",
            "<?php system($_GET['c']); ?>",
            "#!/bin/sh\nrm -rf /",
            "  \n\t<!doctype HTML>",
    })
    @DisplayName("un document exécutable annoncé comme une image est démasqué")
    void documentsExecutables(String contenu) {
        Type reel = SignatureFichier.identifier(contenu.getBytes(StandardCharsets.UTF_8));
        assertThat(reel).isIn(Type.HTML, Type.SCRIPT);
        // Et surtout : il ne correspond à AUCUN type déclaré.
        assertThat(SignatureFichier.correspond(reel, "image/png")).isFalse();
        assertThat(reel.estAcceptable()).isFalse();
    }

    @ParameterizedTest(name = "le SVG est reconnu même précédé de {0} caractères")
    @ValueSource(strings = {
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
            "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
            "<?xml version=\"1.0\"?>\n<!-- une image tout à fait anodine -->\n<svg/>",
    })
    @DisplayName("un SVG est nommé explicitement : c'est le déguisement le plus probable")
    void svgDemasque(String contenu) {
        assertThat(SignatureFichier.identifier(contenu.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Type.SVG);
    }

    @Test
    @DisplayName("un BOM UTF-8 ne masque pas le balisage qui le suit")
    void bomNeMasquePas() {
        byte[] avecBom = concat(octets(0xEF, 0xBB, 0xBF), "<svg xmlns=\"…\"/>");
        assertThat(SignatureFichier.identifier(avecBom)).isEqualTo(Type.SVG);
    }

    @ParameterizedTest(name = "{0} déclaré {1} : correspondance = {2}")
    @CsvSource({
            "PNG,  image/png,  true",
            "PNG,  IMAGE/PNG,  true",
            "JPEG, image/jpeg, true",
            "JPEG, image/png,  false",
            "PNG,  image/jpeg, false",
            "SVG,  image/png,  false",
            "HTML, image/png,  false",
            "PDF,  application/pdf, true",
    })
    @DisplayName("le type déclaré doit correspondre au contenu, à la casse près")
    void correspondance(Type reel, String declare, boolean attendu) {
        assertThat(SignatureFichier.correspond(reel, declare)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("un paramètre de charset ne fait pas échouer la comparaison")
    void charsetIgnore() {
        assertThat(SignatureFichier.correspond(Type.PNG, "image/png; charset=binary")).isTrue();
    }

    @Test
    @DisplayName("une vraie image JPEG annoncée PNG est refusée elle aussi")
    void mensongeInoffensifRefuse() {
        // Elle n'est pas dangereuse. Mais l'accepter, c'est emprunter le chemin
        // de code qui laisserait aussi passer un document HTML.
        assertThat(SignatureFichier.correspond(SignatureFichier.identifier(JPEG), "image/png"))
                .isFalse();
    }

    @Test
    @DisplayName("un PNG suivi d'une charge utile reste un PNG")
    void polyglotteResteUnPng() {
        // Ajouter « <?php … » après un en-tête PNG valide est le déguisement
        // inverse. Le fichier EST un PNG ; ce qui le rendrait dangereux serait
        // qu'un serveur l'interprète, pas qu'il soit déposé.
        assertThat(SignatureFichier.identifier(concat(PNG, "<?php system($_GET['c']); ?>")))
                .isEqualTo(Type.PNG);
    }

    @Test
    @DisplayName("un fichier vide ou tronqué est inconnu, jamais accepté par défaut")
    void fichierTropCourt() {
        assertThat(SignatureFichier.identifier(new byte[0])).isEqualTo(Type.INCONNU);
        assertThat(SignatureFichier.identifier(null)).isEqualTo(Type.INCONNU);
        assertThat(SignatureFichier.identifier(octets(0x89, 'P'))).isEqualTo(Type.INCONNU);
        assertThat(Type.INCONNU.estAcceptable()).isFalse();
    }

    @Test
    @DisplayName("« RIFF » sans « WEBP » n'est pas une image WebP")
    void riffNestPasWebp() {
        // Un fichier WAV commence aussi par RIFF.
        byte[] wav = octets('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E');
        assertThat(SignatureFichier.identifier(wav)).isEqualTo(Type.INCONNU);
    }

    @Test
    @DisplayName("une boîte ftyp d'un autre format n'est pas de l'AVIF")
    void ftypAutreFormat() {
        byte[] mp4 = octets(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm');
        assertThat(SignatureFichier.identifier(mp4)).isEqualTo(Type.INCONNU);
    }
}
