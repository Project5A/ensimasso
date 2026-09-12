package fr.ensim.asso.contenu.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
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
    private final TypeBlocRepository typesBlocs;
    private final ValidationBloc validation;
    private final PolitiqueAcces politique;
    private final ObjectMapper mapper;

    public ServiceContenu(PageRepository pages, PageVersionRepository versions,
                          BlocRepository blocs, MediaUsageRepository mediaUsages,
                          TypeBlocRepository typesBlocs,
                          ValidationBloc validation, PolitiqueAcces politique,
                          ObjectMapper mapper) {
        this.pages = pages;
        this.versions = versions;
        this.blocs = blocs;
        this.mediaUsages = mediaUsages;
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

        int position = (ordre != null) ? ordre : (int) blocs.countByPageVersionId(versionId);
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

        versions.versionPubliee(page.getId()).ifPresent(PageVersion::archiver);
        versions.flush();                       // libère l'index unique partiel

        brouillon.publier(auteur, maintenant);
        indexerMedias(brouillon.getId(), contenu);
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

    @Transactional(readOnly = true)
    public List<Bloc> blocsDe(UUID versionId) {
        return blocs.findByPageVersionIdOrderByOrdreAsc(versionId);
    }

    @Transactional(readOnly = true)
    public List<Page> pagesDuMandat(UUID mandatId) {
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
    private void indexerMedias(UUID versionId, List<Bloc> contenu) {
        Set<String> cles = new LinkedHashSet<>();
        for (Bloc b : contenu) {
            try {
                collecterMediaKeys(mapper.readTree(b.getPayload()), cles);
            } catch (Exception ignore) {
                // le payload a déjà été validé ; une anomalie ici ne doit pas
                // faire échouer une publication par ailleurs valide
            }
        }
        cles.forEach(c -> mediaUsages.save(new MediaUsage(c, versionId)));
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
