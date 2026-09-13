package fr.ensim.asso.portail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import fr.ensim.asso.agenda.app.ServiceAgenda;
import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.media.app.ServiceMedia;
import fr.ensim.asso.partenariat.app.ServicePartenariat;
import fr.ensim.asso.partenariat.domain.Partenaire;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Le rendu public d'une page d'association.
 *
 * <p>Deux décisions structurent tout ce service, et toutes deux viennent de
 * la revue d'architecture :
 *
 * <p><strong>1. Un bloc est résolu contre le mandat DE SA PAGE.</strong> Jamais
 * contre le mandat courant. Un bloc trombinoscope sur la page 2023-2024 affiche
 * le bureau de 2023-2024, définitivement. La revue avait relevé qu'une
 * conception où la référence est « le mandat courant » réécrit silencieusement
 * ses propres archives au premier redéploiement du moteur de rendu — et qu'aucun
 * test ne l'attrape, parce qu'en développement « courant » et « le mandat de la
 * page » sont la même chose.
 *
 * <p><strong>2. Les URL de médias sont fabriquées ici, à la lecture.</strong>
 * Le stockage ne rend que des clés. C'est la correction de STOR-01 en action :
 * une page d'archive de 2023 affiche encore ses images en 2029, parce que rien
 * de périssable n'a jamais été écrit en base.
 */
@Service
public class ServicePortail {

    private final AssociationRepository associations;
    private final MandatRepository mandats;
    private final MembreBureauRepository membres;
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final ThemeVersionRepository themes;
    private final ServiceContenu contenu;
    private final ServiceMedia medias;
    private final ServiceAgenda agenda;
    private final ServicePartenariat partenariats;
    private final PolitiqueAcces politique;
    private final PortCache cache;
    private final Duration dureeCache;

    /**
     * Les deux seules mesures qui répondent à une question qu'on se pose
     * vraiment : « le cache sert-il à quelque chose ? » et « combien coûte le
     * rendu d'une page quand il n'a pas servi ? ». Une métrique qu'on ne sait
     * pas relier à une décision est une métrique qu'on paiera à stocker sans
     * jamais la regarder.
     */
    private final Counter cacheSucces;
    private final Counter cacheDefauts;
    private final Timer dureeRendu;
    private final AnneeUniversitaireRepository annees;
    private final ObjectMapper mapper;
    private final java.time.Clock horloge;

    public ServicePortail(AssociationRepository associations, MandatRepository mandats,
                          MembreBureauRepository membres, PageRepository pages,
                          PageVersionRepository versions, ThemeVersionRepository themes,
                          ServiceContenu contenu, ServiceMedia medias,
                          ServiceAgenda agenda, ServicePartenariat partenariats,
                          AnneeUniversitaireRepository annees, PolitiqueAcces politique,
                          PortCache cache,
                          @Value("${ensimasso.cache.duree:PT10M}") Duration dureeCache,
                          MeterRegistry metriques,
                          ObjectMapper mapper, java.time.Clock horloge) {
        this.associations = associations;
        this.mandats = mandats;
        this.membres = membres;
        this.pages = pages;
        this.versions = versions;
        this.themes = themes;
        this.contenu = contenu;
        this.medias = medias;
        this.agenda = agenda;
        this.partenariats = partenariats;
        this.politique = politique;
        this.cache = cache;
        // Plafonnée par la validité des URL de médias : une page mémorisée plus
        // longtemps que ses URL signées afficherait des images mortes. La règle
        // est ici, pas dans un commentaire de configuration, pour qu'une valeur
        // trop généreuse dans un fichier .env ne puisse pas la contourner.
        this.dureeCache = plafonner(dureeCache, medias.validiteUrlLecture());

        this.cacheSucces = Counter.builder("ensimasso.portail.cache")
                .description("Lectures du cache du portail public")
                .tag("resultat", "succes").register(metriques);
        this.cacheDefauts = Counter.builder("ensimasso.portail.cache")
                .description("Lectures du cache du portail public")
                .tag("resultat", "defaut").register(metriques);
        this.dureeRendu = Timer.builder("ensimasso.portail.rendu")
                .description("Rendu complet d'une page, cache non servi")
                // La moyenne d'un temps de rendu ne dit rien : c'est la queue
                // qui fait qu'un visiteur attend. On publie la médiane et le
                // 95e centile, pas une moyenne rassurante.
                .publishPercentiles(0.5, 0.95)
                .register(metriques);

        // Le nombre d'associations réellement dirigées. Si cette valeur tombe à
        // zéro, plus aucune page publique ne s'affiche : c'est l'alerte la plus
        // utile du système, et elle ne se déduit d'aucune métrique technique.
        io.micrometer.core.instrument.Gauge
                .builder("ensimasso.gouvernance.mandats.en_fonction",
                         () -> mandats.countByStatut(StatutMandat.EN_FONCTION))
                .description("Associations ayant un bureau en fonction")
                .register(metriques);
        this.annees = annees;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    /** L'annuaire public : une association par ligne, avec son mandat en cours. */
    @Transactional(readOnly = true)
    public List<PageRendue.AssociationVue> annuaire() {
        return associations.findAll().stream()
                .sorted(Comparator.comparing(Association::getSlug))
                .map(a -> new PageRendue.AssociationVue(
                        a.getSlug(), a.getNom(), a.getTypeAsso().name()))
                .toList();
    }

    /** La page publiée d'une association, pour son mandat en fonction. */
    @Transactional(readOnly = true)
    public PageRendue page(String slugAsso, String slugPage) {
        Association asso = association(slugAsso);
        Mandat mandat = mandats.mandatEnFonction(asso.getId()).orElseThrow(() ->
                new Erreurs.Introuvable("mandat en fonction pour", slugAsso));
        return rendre(asso, mandat, slugPage, true);
    }

    /**
     * La page d'une année passée. C'est l'archive, et elle ne coûte aucun code
     * particulier : une autre année est simplement un autre mandat.
     */
    @Transactional(readOnly = true)
    public PageRendue pageArchivee(String slugAsso, String anneeCode, String slugPage) {
        Association asso = association(slugAsso);
        Mandat mandat = mandats.findByAssociationIdAndAnneeCode(asso.getId(), anneeCode)
                .orElseThrow(() -> new Erreurs.Introuvable(
                        "mandat " + anneeCode + " pour", slugAsso));
        if (mandat.getStatut() == StatutMandat.PREPARATION) {
            // Un mandat en préparation est invisible : le bureau entrant
            // travaille ses brouillons, le public ne doit rien en voir.
            throw new Erreurs.Introuvable("mandat publié " + anneeCode + " pour", slugAsso);
        }
        return rendre(asso, mandat, slugPage, false);
    }

    // ------------------------------------------------------------- interne

    /**
     * L'aperçu d'un brouillon.
     *
     * <p>Il emprunte exactement le même chemin de rendu que la page publique —
     * mêmes blocs résolus, même thème, même agenda du mandat de la page. C'est
     * la seule façon qu'un aperçu dise la vérité : un rendu parallèle diverge,
     * et il diverge silencieusement.
     *
     * <p>La seule différence est l'autorisation. Un brouillon n'est pas public :
     * le voir exige le droit d'éditer les pages de ce mandat, vérifié ici et
     * non par un motif d'URL.
     */
    @Transactional(readOnly = true)
    public PageRendue apercu(UUID demandeur, UUID versionId) {
        PageVersion version = versions.findById(versionId)
                .orElseThrow(() -> new Erreurs.Introuvable("version", versionId));
        Page page = pages.findById(version.getPageId())
                .orElseThrow(() -> new Erreurs.Introuvable("page de la version", versionId));
        Mandat mandat = mandats.findById(page.getMandatId())
                .orElseThrow(() -> new Erreurs.Introuvable("mandat de la page", page.getId()));

        politique.exigerSurMandat(demandeur, Permission.PAGE_EDITER, mandat.getId());

        Association asso = associations.findById(mandat.getAssociationId())
                .orElseThrow(() -> new Erreurs.Introuvable("association", mandat.getAssociationId()));

        return rendre(asso, mandat, page, version, mandat.getStatut() == StatutMandat.EN_FONCTION);
    }

    private PageRendue rendre(Association asso, Mandat mandat, String slugPage, boolean estCourant) {
        Page page = pages.findByMandatIdAndSlug(mandat.getId(), slugPage)
                .orElseThrow(() -> new Erreurs.Introuvable("page", slugPage));

        PageVersion publiee = versions.versionPubliee(page.getId()).orElseThrow(() ->
                new Erreurs.Introuvable("version publiée de la page", slugPage));

        // La clé contient l'identifiant de version : publier écrit une NOUVELLE
        // clé, et l'ancienne s'éteint seule. Il n'y a donc rien à invalider —
        // c'est-à-dire rien à rater. L'aperçu d'un brouillon, lui, appelle le
        // rendu directement : il n'est jamais mémorisé.
        String cle = cleDe(asso, page, publiee.getId(), estCourant);
        Optional<PageRendue> memorisee = cache.lire(cle).flatMap(this::relire);
        if (memorisee.isPresent()) {
            cacheSucces.increment();
            return memorisee.get();
        }
        cacheDefauts.increment();

        PageRendue rendue = dureeRendu.record(() -> rendre(asso, mandat, page, publiee, estCourant));
        ecrire(cle, rendue);
        return rendue;
    }

    /**
     * Ce qui n'est PAS couvert par la clé : le thème du mandat, le menu, l'agenda
     * et les partenaires. Ces données changent sans créer de version de page, et
     * peuvent donc accuser un retard borné par la durée du cache. C'est le prix
     * assumé d'une clé qu'on peut calculer sans faire le travail qu'on cherche à
     * éviter ; la durée est courte pour cette raison.
     */
    static String cleDe(Association asso, Page page, UUID versionId, boolean estCourant) {
        return "portail:v1:" + asso.getSlug() + ':' + page.getMandatId() + ':'
             + page.getSlug() + ':' + versionId + ':' + (estCourant ? "courant" : "archive");
    }

    /** Le cache est un confort : une valeur illisible se jette, elle n'échoue pas. */
    private Optional<PageRendue> relire(String json) {
        try {
            return Optional.of(mapper.readValue(json, PageRendue.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void ecrire(String cle, PageRendue rendue) {
        try {
            cache.ecrire(cle, mapper.writeValueAsString(rendue), dureeCache);
        } catch (Exception e) {
            // Ne pas savoir mémoriser une page n'est pas une raison de ne pas
            // la servir.
        }
    }

    /**
     * La durée au-delà de laquelle une page mémorisée afficherait des images
     * mortes — cache serveur comme cache du navigateur.
     *
     * <p>Le plafond était appliqué ici et nulle part ailleurs. Le contrôleur
     * posait, lui, un {@code Cache-Control} de son cru : cinq minutes pour la
     * page vivante, <em>une heure</em> pour les archives, au motif qu'une
     * archive ne change plus. Sauf que ce qui expire n'est pas la page, ce sont
     * les URL signées qu'elle contient : le navigateur gardait une heure une
     * page dont les images mouraient au bout de trente minutes. C'est STOR-01
     * qui revenait par la porte de derrière — exactement ce que ce plafond
     * existe pour empêcher.
     */
    public Duration dureeCachePublic() {
        return dureeCache;
    }

    static Duration plafonner(Duration demandee, Duration validiteDesUrls) {
        Duration maximum = validiteDesUrls.dividedBy(2);
        return demandee.compareTo(maximum) > 0 ? maximum : demandee;
    }

    private PageRendue rendre(Association asso, Mandat mandat, Page page,
                              PageVersion publiee, boolean estCourant) {
        List<Bloc> blocs = contenu.blocsDe(publiee.getId()).stream()
                .filter(Bloc::isVisible)
                .toList();

        // Le bureau DU MANDAT DE LA PAGE. Résolu une fois, réutilisé par tous
        // les blocs qui en ont besoin.
        List<PageRendue.MembreVue> equipe = equipeDe(mandat.getId());

        // Agenda et partenaires sont résolus une fois pour la page, et seulement
        // si un bloc les demande : une page sans agenda ne doit pas payer une
        // requête pour rien.
        List<Evenement> tousEvenements = blocs.stream().anyMatch(b -> "EVENT_LIST".equals(b.getType()))
                ? agenda.publicsDuMandat(mandat.getId()) : List.of();
        List<Partenaire> tousPartenaires = blocs.stream().anyMatch(b -> "PARTNERS".equals(b.getType()))
                ? partenariats.visiblesDuMandat(mandat.getId()) : List.of();

        List<PageRendue.BlocRendu> rendus = blocs.stream()
                .map(b -> rendreBloc(b, equipe, tousEvenements, tousPartenaires, estCourant))
                .toList();

        List<PageRendue.PageLien> menu = pages.findByMandatIdOrderByOrdreMenuAsc(mandat.getId())
                .stream()
                .filter(p -> versions.versionPubliee(p.getId()).isPresent())
                .map(p -> new PageRendue.PageLien(p.getSlug(), p.getTitre(), p.getOrdreMenu()))
                .toList();

        Map<String, Object> theme = themes.versionPubliee(mandat.getId())
                .map(t -> lireJson(t.getTokens()))
                .orElseGet(Map::of);

        List<String> anneesPubliees = mandats.findByAssociationIdOrderByDebutLeDesc(asso.getId())
                .stream()
                .filter(m -> m.getStatut() != StatutMandat.PREPARATION)
                .map(Mandat::getAnneeCode)
                .toList();

        return new PageRendue(
                new PageRendue.AssociationVue(asso.getSlug(), asso.getNom(), asso.getTypeAsso().name()),
                new PageRendue.MandatVue(mandat.getAnneeCode(), mandat.getStatut().name(), estCourant),
                page.getSlug(), page.getTitre(), publiee.getNumero(), publiee.getPublieLe(),
                theme, rendus, menu, anneesPubliees);
    }

    private PageRendue.BlocRendu rendreBloc(Bloc bloc, List<PageRendue.MembreVue> equipe,
                                           List<Evenement> tousEvenements,
                                           List<Partenaire> tousPartenaires,
                                           boolean estCourant) {
        Map<String, Object> payload = lireJson(bloc.getPayload());

        List<Evenement> evenements = "EVENT_LIST".equals(bloc.getType())
                ? SelectionBlocs.agenda(payload, tousEvenements, estCourant,
                                        OffsetDateTime.now(horloge))
                : List.of();
        List<Partenaire> partenaires = "PARTNERS".equals(bloc.getType())
                ? SelectionBlocs.partenaires(payload, tousPartenaires) : List.of();

        // Les clés de médias sont résolues MAINTENANT. Rien de périssable
        // n'est jamais écrit en base : c'est toute la correction de STOR-01.
        // Les affiches d'évènements et les logos de partenaires suivent la même
        // règle, et sont donc résolus ici comme le reste.
        Set<String> cles = new LinkedHashSet<>();
        collecterCles(payload, cles);
        evenements.stream().map(Evenement::getMediaKey).filter(Objects::nonNull).forEach(cles::add);
        partenaires.stream().map(Partenaire::getLogoMediaKey).filter(Objects::nonNull).forEach(cles::add);
        Map<String, String> urls = cles.isEmpty() ? Map.of() : medias.urlsDe(cles);

        // Seul un bloc trombinoscope reçoit l'équipe : inutile de la répéter.
        List<PageRendue.MembreVue> equipeDuBloc =
                "TEAM_GRID".equals(bloc.getType()) ? equipe : List.of();

        return new PageRendue.BlocRendu(bloc.getId(), bloc.getType(), bloc.getSchemaVersion(),
                payload, urls, equipeDuBloc,
                evenements.stream().map(e -> vue(e, urls)).toList(),
                partenaires.stream().map(pa -> vue(pa, urls)).toList());
    }

    private PageRendue.EvenementVue vue(Evenement e, Map<String, String> urls) {
        return new PageRendue.EvenementVue(
                e.getSlug(), e.getTitre(), e.getResume(), e.getLieu(),
                e.getDebutLe(), e.getFinLe(), e.getStatut().name(), e.isComplet(),
                e.getMotifAnnulation(), e.getLien(),
                e.getMediaKey() == null ? null : urls.get(e.getMediaKey()));
    }

    private PageRendue.PartenaireVue vue(Partenaire p, Map<String, String> urls) {
        return new PageRendue.PartenaireVue(
                p.getNom(), p.getNiveau().name(), p.getUrl(),
                p.getLogoMediaKey() == null ? null : urls.get(p.getLogoMediaKey()));
    }

    private List<PageRendue.MembreVue> equipeDe(UUID mandatId) {
        List<MembreBureau> actifs = membres.membresActifs(mandatId).stream()
                .filter(MembreBureau::isVisiblePublic)
                .toList();

        Set<String> clesPhotos = actifs.stream()
                .map(MembreBureau::getPhotoMediaKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> urls = clesPhotos.isEmpty() ? Map.of() : medias.urlsDe(clesPhotos);

        return actifs.stream()
                .map(m -> new PageRendue.MembreVue(
                        m.getPoste().name(),
                        m.getTitreAffiche() != null ? m.getTitreAffiche() : libelle(m.getPoste()),
                        m.getOrdre(),
                        m.getPhotoMediaKey() == null ? null : urls.get(m.getPhotoMediaKey())))
                .toList();
    }

    /** Libellé français par défaut d'un poste, quand l'asso n'en impose pas. */
    private String libelle(Poste poste) {
        return switch (poste) {
            case PRESIDENT -> "Président·e";
            case VICE_PRESIDENT -> "Vice-président·e";
            case TRESORIER -> "Trésorier·ère";
            case SECRETAIRE -> "Secrétaire";
            case RESP_COM -> "Responsable communication";
            case RESP_EVENEMENTS -> "Responsable évènements";
            case MEMBRE_BUREAU -> "Membre du bureau";
        };
    }

    private Association association(String slug) {
        return associations.findBySlug(slug)
                .orElseThrow(() -> new Erreurs.Introuvable("association", slug));
    }

    private Map<String, Object> lireJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void collecterCles(Object noeud, Set<String> sortie) {
        if (noeud instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (("mediaKey".equals(k) || "photoMediaKey".equals(k)) && v instanceof String s) {
                    sortie.add(s);
                } else if ("mediaKeys".equals(k) && v instanceof List<?> l) {
                    l.forEach(e -> { if (e instanceof String s) sortie.add(s); });
                } else {
                    collecterCles(v, sortie);
                }
            });
        } else if (noeud instanceof List<?> list) {
            list.forEach(e -> collecterCles(e, sortie));
        }
    }
}
