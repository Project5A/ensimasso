package fr.ensim.asso.media.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Identifie un fichier par ses premiers octets, et non par ce que le client
 * déclare.
 *
 * <p><strong>Pourquoi c'est nécessaire.</strong> Le dépôt est direct vers le
 * stockage objet : le navigateur signe un PUT et choisit lui-même l'en-tête
 * {@code Content-Type}. Relire ce type auprès de S3 à la confirmation — ce que
 * faisait déjà le service — revient donc à relire ce que le client a écrit. Un
 * document HTML porteur de script, annoncé {@code image/png}, franchissait ce
 * contrôle sans difficulté.
 *
 * <p><strong>Pourquoi c'est sûr de le faire ici.</strong> Comparer des octets à
 * des constantes n'invoque aucun analyseur : il n'y a ni décodeur d'image, ni
 * parseur XML exposé à un fichier hostile. Décoder, redimensionner ou retirer
 * les métadonnées EXIF est une tout autre affaire, et reste le travail du
 * worker média — dans un processus isolé, sans accès à la base.
 *
 * <p>Les SVG sont reconnus et nommés explicitement plutôt que simplement
 * « inconnus » : c'est le déguisement le plus probable, et un refus qui dit
 * pourquoi évite qu'on le contourne en renommant le fichier.
 */
public final class SignatureFichier {

    /**
     * Assez pour couvrir une déclaration XML suivie d'une balise {@code <svg>},
     * qui peut être précédée de commentaires. Lire davantage ne coûte rien : la
     * lecture est une requête par plage, pas un téléchargement.
     */
    public static final int OCTETS_A_LIRE = 512;

    public enum Type {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp"),
        AVIF("image/avif"),
        GIF("image/gif"),
        PDF("application/pdf"),

        // Les types ci-dessous ne sont jamais acceptés. Ils existent pour que
        // le refus soit explicite.
        SVG(null),
        HTML(null),
        SCRIPT(null),
        INCONNU(null);

        private final String contentType;

        Type(String contentType) { this.contentType = contentType; }

        /** Le type MIME correspondant, ou {@code null} si le format est refusé. */
        public String contentType() { return contentType; }

        public boolean estAcceptable() { return contentType != null; }
    }

    private SignatureFichier() { }

    /** Identifie le format réel à partir des premiers octets du fichier. */
    public static Type identifier(byte[] debut) {
        if (debut == null || debut.length < 4) {
            return Type.INCONNU;
        }

        if (commencePar(debut, 0, 0xFF, 0xD8, 0xFF)) return Type.JPEG;
        if (commencePar(debut, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Type.PNG;
        if (texteA(debut, 0, "GIF87a") || texteA(debut, 0, "GIF89a")) return Type.GIF;
        if (texteA(debut, 0, "%PDF-")) return Type.PDF;

        // RIFF....WEBP — la taille occupe les octets 4 à 7.
        if (texteA(debut, 0, "RIFF") && texteA(debut, 8, "WEBP")) return Type.WEBP;

        // Boîte ISO-BMFF : taille, « ftyp », puis la marque principale.
        if (texteA(debut, 4, "ftyp") && (texteA(debut, 8, "avif") || texteA(debut, 8, "avis"))) {
            return Type.AVIF;
        }

        return identifierTexte(debut);
    }

    /**
     * Le format réel correspond-il au type déclaré ?
     *
     * <p>Une image JPEG annoncée {@code image/png} est refusée elle aussi. Elle
     * n'est pas dangereuse, mais accepter le mensonge revient à ne pas vérifier :
     * c'est le même chemin de code qui laisserait passer un document HTML.
     */
    public static boolean correspond(Type reel, String contentTypeDeclare) {
        return reel.estAcceptable()
            && reel.contentType().equalsIgnoreCase(normaliser(contentTypeDeclare));
    }

    // ------------------------------------------------------------- interne

    /** Balisage et scripts, à travers un éventuel BOM ou des espaces de tête. */
    private static Type identifierTexte(byte[] debut) {
        int i = 0;
        if (debut.length >= 3 && commencePar(debut, 0, 0xEF, 0xBB, 0xBF)) {
            i = 3;                                   // BOM UTF-8
        }
        while (i < debut.length && Character.isWhitespace((char) (debut[i] & 0xFF))) {
            i++;
        }

        if (i < debut.length && (debut[i] & 0xFF) == '#'
                && i + 1 < debut.length && (debut[i + 1] & 0xFF) == '!') {
            return Type.SCRIPT;
        }
        if (i >= debut.length || (debut[i] & 0xFF) != '<') {
            return Type.INCONNU;
        }

        String tete = new String(debut, i, debut.length - i, StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);

        if (tete.startsWith("<?php")) return Type.SCRIPT;
        // Un SVG commence souvent par une déclaration XML, parfois par un
        // DOCTYPE : on cherche la balise, pas le premier caractère.
        if (tete.contains("<svg")) return Type.SVG;
        if (tete.startsWith("<!doctype html") || tete.startsWith("<html")
                || tete.startsWith("<head") || tete.startsWith("<script")) {
            return Type.HTML;
        }
        if (tete.startsWith("<?xml")) return Type.INCONNU;
        return Type.HTML;                            // toute autre balise ouvrante
    }

    private static boolean commencePar(byte[] octets, int depart, int... attendus) {
        if (octets.length < depart + attendus.length) return false;
        for (int i = 0; i < attendus.length; i++) {
            if ((octets[depart + i] & 0xFF) != (attendus[i] & 0xFF)) return false;
        }
        return true;
    }

    private static boolean texteA(byte[] octets, int depart, String attendu) {
        if (octets.length < depart + attendu.length()) return false;
        for (int i = 0; i < attendu.length(); i++) {
            if ((octets[depart + i] & 0xFF) != attendu.charAt(i)) return false;
        }
        return true;
    }

    /** « image/png; charset=utf-8 » et « IMAGE/PNG » désignent le même type. */
    private static String normaliser(String contentType) {
        if (contentType == null) return "";
        int pointVirgule = contentType.indexOf(';');
        return (pointVirgule >= 0 ? contentType.substring(0, pointVirgule) : contentType).trim();
    }
}
