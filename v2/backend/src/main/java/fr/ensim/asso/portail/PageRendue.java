package fr.ensim.asso.portail;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une page publique, entièrement résolue et prête à rendre.
 *
 * <p>Ce sont des enregistrements, jamais des entités JPA : un test ArchUnit
 * échoue si une méthode d'API renvoie une {@code @Entity}. C'est la correction
 * structurelle de ARCH-01/SEC-03, la faille qui faisait servir publiquement les
 * empreintes de mots de passe de la v1.
 */
public record PageRendue(
        AssociationVue association,
        MandatVue mandat,
        String slug,
        String titre,
        int versionNumero,
        OffsetDateTime publieLe,
        Map<String, Object> theme,
        List<BlocRendu> blocs,
        List<PageLien> menu,
        List<String> anneesDisponibles) {

    public record AssociationVue(String slug, String nom, String type) { }

    /** Le mandat DE LA PAGE, pas le mandat courant — c'est ce qui rend l'archive fidèle. */
    public record MandatVue(String anneeCode, String statut, boolean estCourant) { }

    public record PageLien(String slug, String titre, int ordreMenu) { }

    /**
     * Un bloc résolu : son payload, plus ce que le serveur a dû aller chercher
     * (URL de médias, membres du bureau) au moment du rendu.
     */
    public record BlocRendu(UUID id, String type, int schemaVersion,
                            Map<String, Object> payload,
                            Map<String, String> urlsMedias,
                            List<MembreVue> equipe) { }

    /**
     * Un membre du bureau, réduit à ce qui est public.
     *
     * <p>Ni identifiant de personne, ni e-mail : le trombinoscope d'une page
     * publique n'a pas besoin de l'identité technique, et une page d'archive
     * consultable par tout internet encore moins.
     */
    public record MembreVue(String poste, String titreAffiche, int ordre, String photoUrl) { }
}
