package fr.ensim.asso.contenu.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Mandat;
import fr.ensim.asso.gouvernance.domain.MandatRepository;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.media.domain.MediasPublicables;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Édition et publication des pages.
 *
 * <p>Publier crée une nouvelle version et archive la précédente, dans une seule
 * transaction locale. C'est exactement la raison pour laquelle contenu et
 * gouvernance vivent dans le même processus : à travers un réseau, cette
 * opération deviendrait une saga avec compensation, pour un site consulté par
 * quelques milliers d'étudiants.
 */
@Service
public class ServiceContenu {

    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final BlocRepository blocs;
    private final MediaUsageRepository mediaUsages;
    private final MediaAssetRepository medias;
    private final MandatRepository mandats;
    private final TypeBlocRepository typesBlocs;
    private final ValidationBloc validation;
    private final PolitiqueAcces politique;
    private final ObjectMapper mapper;

    public ServiceContenu(PageRepository pages, PageVersionRepository versions,
                          BlocRepository blocs, MediaUsageRepository mediaUsages,
                          MediaAssetRepository medias, MandatRepository mandats,
                          TypeBlocRepository typesBlocs,
                          ValidationBloc validation, PolitiqueAcces politique,
                          ObjectMapper mapper) {
        this.pages = pages;
        this.versions = versions;
        this.blocs = blocs;
        this.mediaUsages = mediaUsages;
        this.medias = medias;
        this.mandats = mandats;
        this.typesBlocs = typesBlocs;
        this.validation = validation;
        this.politique = politique;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ édition

    @Transactional
    public Page creerPage(UUID auteur, UUID mandatId, String slug, String titre, int ordreMenu) {
        politique.exigerSurMandat(auteur, Permission.PAGE_EDITER, mandatId);
        pages.findByMandatIdAndSlug(mandatId, slug).ifPresent(p -> {
            throw new Erreurs.Conflit("une page « " + slug + " » existe déjà pour ce mandat");
        });
        return pages.save(new Page(mandatId, slug, titre, ordreMenu));
    }

    /**
     * Ouvre un brouillon à partir de la version publiée (ou vierge s'il n'y en
     * a pas). Éditer une page publiée ne la modifie jamais : on bifurque.
     */
    @Transactional
    public PageVersion ouvrirBrouillon(UUID auteur, UUID pageId) {
        Page page = page(pageId);
        politique.exigerSurMandat(auteur, Permission.PAGE_EDITER, page.getMandatId());

        List<PageVersion> existants = versions.brouillons(pageId);
        if (!existants.isEmpty()) {
            return existants.get(0);            // un seul brouillon à la fois
        }

        PageVersion brouillon = versions.save(
                new PageVersion(pageId, versions.dernierNumero(pageId) + 1, auteur));

        // Repartir du contenu publié, s'il existe.
        versions.versionPubliee(pageId).ifPresent(publiee ->
                blocs.findByPageVersionIdOrderByOrdreAsc(publiee.getId()).forEach(src ->
                        blocs.save(new Bloc(brouillon.getId(), src.getOrdre(), src.getType(),
                                src.getSchemaVersion(), src.getPayload()))));

        return brouillon;
    }

    @Transactional
    public Bloc ajouterBloc(UUID auteur, UUID versionId, String type, String payloadJson, Integer ordre) {
        PageVersion version = versionModifiable(auteur, versionId);
        int schemaVersion = validation.versionCourantePour(type);
        validation.valider(type, schemaVersion, payloadJson);

        // max+1, et non count() : une suppression au milieu d'une page laisse un
        // trou, après quoi count() désigne un ordre DÉJÀ PRIS. Blocs 0,1,2,
        // retirer le 1 : count() vaut 2, la contrainte bloc_ordre_unique refuse,
        // et l'ajout sort en 500. Une seule suppression suffisait à bloquer
        // l'éditeur jusqu'au prochain réordonnancement.
        int position = (ordre != null) ? ordre : blocs.dernierOrdre(versionId) + 1;
        if (ordre != null && blocs.existsByPageVersionIdAndOrdre(versionId, ordre)) {
            // Une position imposée et déjà occupée est une erreur de l'appelant,
            // pas une panne : la dire en 409 vaut mieux que de la laisser
            // remonter en violation de contrainte.
            throw new Erreurs.Conflit(
                    "la position " + ordre + " est déjà occupée dans cette version");
        }
        return blocs.save(new Bloc(version.getId(), position, type, schemaVersion, payloadJson));
    }

    @Transactional
    public Bloc modifierBloc(UUID auteur, UUID blocId, String payloadJson) {
        Bloc bloc = blocs.findById(blocId)
                .orElseThrow(() -> new Erreurs.Introuvable("bloc", blocId));
        versionModifiable(auteur, bloc.getPageVersionId());

        // On valide contre la version de schéma DU BLOC, pas la version courante.
        validation.valider(bloc.getType(), bloc.getSchemaVersion(), payloadJson);
        bloc.remplacerPayload(payloadJson);
        return bloc;
    }

    @Transactional
    public void supprimerBloc(UUID auteur, UUID blocId) {
        Bloc bloc = blocs.findById(blocId)
                .orElseThrow(() -> new Erreurs.Introuvable("bloc", blocId));
        versionModifiable(auteur, bloc.getPageVersionId());
        blocs.delete(bloc);
    }

    /** Réordonne l'intégralité des blocs d'un brouillon, en une fois. */
    @Transactional
    public void reordonner(UUID auteur, UUID versionId, List<UUID> ordreVoulu) {
        versionModifiable(auteur, versionId);
        List<Bloc> actuels = blocs.findByPageVersionIdOrderByOrdreAsc(versionId);

        Set<UUID> attendus = new HashSet<>(actuels.stream().map(Bloc::getId).toList());
        if (!attendus.equals(new HashSet<>(ordreVoulu)) || ordreVoulu.size() != actuels.size()) {
            throw new Erreurs.Conflit(
                    "le réordonnancement doit lister exactement les blocs de la version");
        }
        // Décalage temporaire hors plage : la contrainte d'unicité (version, ordre)
        // est immédiate, on ne peut donc pas permuter en place.
        Map<UUID, Bloc> parId = new HashMap<>();
        actuels.forEach(b -> parId.put(b.getId(), b));
        int tampon = actuels.size() + 1000;
        for (Bloc b : actuels) {
            b.deplacer(tampon++);
        }
        blocs.flush();
        for (int i = 0; i < ordreVoulu.size(); i++) {
            parId.get(ordreVoulu.get(i)).deplacer(i);
        }
    }

    // -------------------------------------------------------- publication

    /**
     * Publie un brouillon. En une transaction : archiver la version publiée,
     * promouvoir le brouillon, recalculer les médias référencés.
     */
    @Transactional
    public PageVersion publier(UUID auteur, UUID versionId, java.time.OffsetDateTime maintenant) {
        PageVersion brouillon = versions.findById(versionId)
                .orElseThrow(() -> new Erreurs.Introuvable("version", versionId));
        Page page = page(brouillon.getPageId());
        politique.exigerSurMandat(auteur, Permission.PAGE_PUBLIER, page.getMandatId());

        if (brouillon.getStatut() != StatutVersion.BROUILLON) {
            throw new Erreurs.Conflit("cette version n'est pas un brouillon");
        }
        List<Bloc> contenu = blocs.findByPageVersionIdOrderByOrdreAsc(versionId);
        if (contenu.isEmpty()) {
            throw new Erreurs.Conflit("une page vide ne peut pas être publiée");
        }

        // Revalider chaque bloc contre SA version de schéma avant de figer.
        contenu.forEach(b -> validation.valider(b.getType(), b.getSchemaVersion(), b.getPayload()));

        // Les médias AVANT de figer quoi que ce soit : une page publiée est
        // immuable, et une image manquante y resterait manquante pour toujours.
        Set<String> clesMedias = clesMediasDe(contenu);
        verifierMedias(page, clesMedias);

        versions.versionPubliee(page.getId()).ifPresent(PageVersion::archiver);
        versions.flush();                       // libère l'index unique partiel

        brouillon.publier(auteur, maintenant);
        clesMedias.forEach(c -> mediaUsages.save(new MediaUsage(c, brouillon.getId())));
        return brouillon;
    }

    /** Rétablit une version antérieure en la reclonant dans un brouillon. */
    @Transactional
    public PageVersion restaurer(UUID auteur, UUID pageId, UUID versionSourceId) {
        Page page = page(pageId);
        politique.exigerSurMandat(auteur, Permission.PAGE_PUBLIER, page.getMandatId());

        PageVersion source = versions.findById(versionSourceId)
                .orElseThrow(() -> new Erreurs.Introuvable("version", versionSourceId));
        if (!source.getPageId().equals(pageId)) {
            throw new Erreurs.Conflit("cette version n'appartient pas à cette page");
        }

        PageVersion brouillon = versions.save(
                new PageVersion(pageId, versions.dernierNumero(pageId) + 1, auteur));
        brouillon.setNote("restauration de la version " + source.getNumero());

        // On reclone tel quel, y compris la version de schéma d'origine : c'est
        // ce qui permet de restaurer un contenu écrit sous un schéma plus ancien.
        blocs.findByPageVersionIdOrderByOrdreAsc(source.getId()).forEach(src ->
                blocs.save(new Bloc(brouillon.getId(), src.getOrdre(), src.getType(),
                        src.getSchemaVersion(), src.getPayload())));

        return brouillon;
    }

    // ------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public Optional<PageVersion> versionPubliee(UUID pageId) {
        return versions.versionPubliee(pageId);
    }

    /**
     * Les blocs d'une version, SANS contrôle d'accès.
     *
     * <p>Réservé au rendu public, qui n'a pas d'utilisateur et qui a déjà
     * établi son droit autrement : il ne résout jamais qu'une version PUBLIEE,
     * du mandat demandé, d'une association publique. Toute lecture faite AU NOM
     * de quelqu'un passe par {@link #blocsPourEdition}.
     */
    @Transactional(readOnly = true)
    public List<Bloc> blocsDe(UUID versionId) {
        return blocs.findByPageVersionIdOrderByOrdreAsc(versionId);
    }

    /**
     * Les blocs d'une version, pour le tableau de bord.
     *
     * <p>Cette lecture-ci n'exerçait AUCUNE autorisation, et elle sert les
     * BROUILLONS : tout compte authentifié — n'importe quel étudiant de
     * l'école — pouvait lire le contenu non publié de n'importe quelle
     * association, y compris celui d'un mandat en PREPARATION, c'est-à-dire le
     * site que le bureau entrant prépare avant l'assemblée générale.
     */
    @Transactional(readOnly = true)
    public List<Bloc> blocsPourEdition(UUID demandeur, UUID versionId) {
        PageVersion version = versions.findById(versionId)
                .orElseThrow(() -> new Erreurs.Introuvable("version", versionId));
        Page page = pages.findById(version.getPageId())
                .orElseThrow(() -> new Erreurs.Introuvable("page", version.getPageId()));
        politique.exigerMembre(demandeur, page.getMandatId());
        return blocs.findByPageVersionIdOrderByOrdreAsc(versionId);
    }

    /** Les pages d'un mandat, brouillons compris : réservé à son bureau. */
    @Transactional(readOnly = true)
    public List<Page> pagesDuMandat(UUID demandeur, UUID mandatId) {
        politique.exigerMembre(demandeur, mandatId);
        return pages.findByMandatIdOrderByOrdreMenuAsc(mandatId);
    }

    /**
     * Le catalogue des types de blocs, avec leur JSON Schema. Le tableau de
     * bord génère ses formulaires d'édition à partir de là : ajouter un type
     * de bloc ne demande donc aucune modification du constructeur de pages.
     */
    @Transactional(readOnly = true)
    public List<TypeBloc> catalogueDesBlocs() {
        return typesBlocs.catalogue();
    }

    // ------------------------------------------------------------- interne

    private Page page(UUID pageId) {
        return pages.findById(pageId)
                .orElseThrow(() -> new Erreurs.Introuvable("page", pageId));
    }

    private PageVersion versionModifiable(UUID auteur, UUID versionId) {
        PageVersion version = versions.findById(versionId)
                .orElseThrow(() -> new Erreurs.Introuvable("version", versionId));
        Page page = page(version.getPageId());
        politique.exigerSurMandat(auteur, Permission.PAGE_EDITER, page.getMandatId());
        if (!version.estModifiable()) {
            throw new Erreurs.Conflit(
                    "version figée (" + version.getStatut() + ") : ouvrez un nouveau brouillon");
        }
        return version;
    }

    /**
     * Recense les médias référencés par la version. Les clés vivent dans du
     * JSONB, donc sans intégrité référentielle : cette table est ce qui permet
     * ensuite de refuser la suppression d'une image encore utilisée.
     */
    private Set<String> clesMediasDe(List<Bloc> contenu) {
        Set<String> cles = new LinkedHashSet<>();
        for (Bloc b : contenu) {
            try {
                collecterMediaKeys(mapper.readTree(b.getPayload()), cles);
            } catch (Exception ignore) {
                // le payload a déjà été validé ; une anomalie ici ne doit pas
                // faire échouer une publication par ailleurs valide
            }
        }
        return cles;
    }

    /**
     * Cette page a-t-elle le droit de servir les médias qu'elle référence ?
     *
     * <p>La clé étrangère de {@code media_usage} refusait déjà une clé
     * inconnue. Elle le faisait au COMMIT, en PostgreSQL, et sans dire
     * laquelle : la publication sortait en 500 avec un message de contrainte,
     * sur une page que son bureau ne pouvait pas réparer sans deviner.
     *
     * <p>Et elle est plus faible qu'elle n'en a l'air. Un média SUPPRIMÉ garde
     * sa ligne — volontairement, pour que les archives restent explicables —
     * donc la clé étrangère accepte encore une image effacée du stockage : la
     * page figée l'aurait affichée cassée, définitivement. De même pour un
     * média encore en ATTENTE_DEPOT ou REJETÉ.
     *
     * <p>Le contrôle ajoute enfin ce que la clé étrangère ne pouvait pas voir :
     * le média doit appartenir à l'association de la page. Rien n'empêchait un
     * bureau d'écrire dans un payload la clé d'un média d'une AUTRE association
     * et de la faire servir par le portail public — qui, sur une page publiée,
     * ne vérifie aucun droit sur les clés, et n'a pas à le faire.
     *
     * <p>Toutes les clés fautives sont nommées d'un coup : corriger une page à
     * raison d'un aller-retour par image serait une punition, pas un message
     * d'erreur.
     */
    private void verifierMedias(Page page, Set<String> cles) {
        if (cles.isEmpty()) {
            return;
        }
        UUID association = mandats.findById(page.getMandatId())
                .map(Mandat::getAssociationId)
                .orElseThrow(() -> new Erreurs.Conflit(
                        "le mandat de cette page est introuvable"));

        List<String> refus = MediasPublicables.refus(medias, association, cles);
        if (!refus.isEmpty()) {
            throw new Erreurs.Conflit(
                    "cette page référence des médias qu'elle ne peut pas publier : "
                  + String.join(" ; ", refus));
        }
    }

    private void collecterMediaKeys(JsonNode noeud, Set<String> sortie) {
        if (noeud.isObject()) {
            noeud.fields().forEachRemaining(e -> {
                if (("mediaKey".equals(e.getKey()) || "photoMediaKey".equals(e.getKey()))
                        && e.getValue().isTextual()) {
                    sortie.add(e.getValue().asText());
                } else if ("mediaKeys".equals(e.getKey()) && e.getValue().isArray()) {
                    e.getValue().forEach(n -> { if (n.isTextual()) sortie.add(n.asText()); });
                } else {
                    collecterMediaKeys(e.getValue(), sortie);
                }
            });
        } else if (noeud.isArray()) {
            noeud.forEach(n -> collecterMediaKeys(n, sortie));
        }
    }

}
