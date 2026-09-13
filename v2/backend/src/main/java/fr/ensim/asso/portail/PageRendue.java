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
        List<String> anneesDisponibles,
        /**
         * L'année du mandat EN FONCTION, s'il y en a un.
         *
         * <p>Sans elle, le portail listait le mandat en cours parmi les
         * « années précédentes » et y menait par l'URL d'archive : la page
         * vivante était servie sous un bandeau annonçant une archive.
         * {@code anneesDisponibles} ne dit pas lequel est lequel ; celle-ci si.
         */
        String anneeCourante) {

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
                            List<MembreVue> equipe,
                            List<EvenementVue> agenda,
                            List<PartenaireVue> partenaires) { }

    /**
     * Un évènement de l'agenda, réduit à ce qui s'affiche.
     *
     * <p>{@code statut} est exposé — et vaut {@code ANNULE} le cas échéant —
     * parce qu'un évènement annulé doit se voir barré plutôt que disparaître :
     * ceux qui avaient prévu de venir ont besoin de le lire.
     */
    public record EvenementVue(String slug, String titre, String resume, String lieu,
                               OffsetDateTime debutLe, OffsetDateTime finLe,
                               String statut, boolean complet, String motifAnnulation,
                               String lien, String afficheUrl) { }

    /** Un partenaire du mandat de la page. */
    public record PartenaireVue(String nom, String niveau, String url, String logoUrl) { }

    /**
     * Un membre du bureau, réduit à ce qui est public.
     *
     * <p>Ni identifiant de personne, ni e-mail : le trombinoscope d'une page
     * publique n'a pas besoin de l'identité technique, et une page d'archive
     * consultable par tout internet encore moins.
     */
    public record MembreVue(String poste, String titreAffiche, int ordre, String photoUrl) { }
}
