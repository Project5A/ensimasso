package fr.ensim.asso.passation;

import fr.ensim.asso.contenu.app.ClonageContenu;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.shared.error.Erreurs;
import fr.ensim.asso.gouvernance.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La passation : le transfert annuel d'un bureau au suivant.
 *
 * <p>C'est la fonctionnalité qui distingue une plateforme d'un site web. Chaque
 * association le fait tous les ans, à la main, et y perd en général le site de
 * l'année précédente.
 *
 * <p><strong>Tout tient dans une seule transaction locale.</strong> Créer le
 * mandat entrant, cloner les pages publiées en brouillons, cloner le thème :
 * réparti entre services, cela deviendrait une saga dont l'échec partiel donne
 * « nouveau bureau, droits complets, site vide, le 1er septembre ».
 *
 * <p>Deux omissions sont <em>délibérées</em> : ni le bureau ni les partenaires
 * ne sont clonés. Recopier automatiquement un trombinoscope est précisément
 * ainsi qu'un site finit par afficher des gens diplômés depuis deux ans.
 */
@Service
public class ServicePassation {

    private final AssociationRepository associations;
    private final MandatRepository mandats;
    private final MembreBureauRepository membres;
    private final PassationRepository passations;
    private final AnneeUniversitaireRepository annees;
    private final ClonageContenu clonage;
    private final PolitiqueAcces politique;

    public ServicePassation(AssociationRepository associations, MandatRepository mandats,
                            MembreBureauRepository membres, PassationRepository passations,
                            AnneeUniversitaireRepository annees, ClonageContenu clonage,
                            PolitiqueAcces politique) {
        this.associations = associations;
        this.mandats = mandats;
        this.membres = membres;
        this.passations = passations;
        this.annees = annees;
        this.clonage = clonage;
        this.politique = politique;
    }

    /**
     * Étape 1 — préparer. Idempotente au sens où une passation déjà ouverte
     * pour cette association est refusée par un index unique partiel : le
     * président nerveux qui clique deux fois n'en crée pas deux.
     */
    @Transactional
    public Passation preparer(UUID demandeur, UUID associationId,
                              OffsetDateTime debutPrevu, OffsetDateTime finPrevue) {

        politique.exiger(demandeur, Permission.PASSATION_LANCER, associationId);

        Association asso = associations.findById(associationId)
                .orElseThrow(() -> new Erreurs.Introuvable("association", associationId));

        Mandat sortant = mandats.mandatEnFonction(associationId)
                .orElseThrow(() -> new IllegalStateException(
                        "aucun mandat en fonction pour " + asso.getSlug()));

        String anneeSuivante = AnneeUniversitaire.anneeSuivante(sortant.getAnneeCode());
        annees.findById(anneeSuivante).orElseThrow(() -> new IllegalStateException(
                "l'année universitaire " + anneeSuivante + " n'est pas déclarée"));

        mandats.findByAssociationIdAndAnneeCode(associationId, anneeSuivante).ifPresent(m -> {
            throw new IllegalStateException("un mandat existe déjà pour " + anneeSuivante);
        });

        Mandat entrant = mandats.save(
                Mandat.enPreparation(associationId, anneeSuivante, debutPrevu, finPrevue));

        // Le contenu publié devient le point de départ du bureau entrant.
        int pagesClonees = clonage.clonerPagesPubliees(sortant.getId(), entrant.getId(), demandeur);
        clonage.clonerTheme(sortant.getId(), entrant.getId());

        return passations.save(new Passation(
                associationId, sortant.getId(), entrant.getId(), demandeur, pagesClonees));
    }

    /**
     * Étape 2 — le président sortant désigne le président entrant, puis le
     * bureau entrant se complète lui-même.
     */
    @Transactional
    public MembreBureau designer(UUID demandeur, UUID passationId, UUID personneId, Poste poste, int ordre) {
        Passation p = passation(passationId);
        exigerOuverte(p);

        boolean sortantAutorise = p.getMandatSortantId() != null
                && membres.posteActif(p.getMandatSortantId(), demandeur)
                        .map(m -> m.getPoste() == Poste.PRESIDENT).orElse(false);
        boolean entrantAutorise = politique.peutSurMandat(
                demandeur, Permission.MEMBRE_GERER, p.getMandatEntrantId());

        if (!sortantAutorise && !entrantAutorise) {
            throw new Erreurs.AccesRefuse(
                    "seul le president sortant ou le bureau entrant peut designer un membre");
        }
        return membres.save(new MembreBureau(p.getMandatEntrantId(), personneId, poste, ordre));
    }

    /** Étape 3 — le bureau entrant est complet (au moins président + trésorier). */
    @Transactional
    public Passation marquerBureauComplete(UUID demandeur, UUID passationId) {
        Passation p = passation(passationId);
        politique.exigerSurMandat(demandeur, Permission.MEMBRE_GERER, p.getMandatEntrantId());

        List<MembreBureau> bureau = membres.membresActifs(p.getMandatEntrantId());
        boolean president = bureau.stream().anyMatch(m -> m.getPoste() == Poste.PRESIDENT);
        boolean tresorier = bureau.stream().anyMatch(m -> m.getPoste() == Poste.TRESORIER);
        if (!president || !tresorier) {
            throw new IllegalStateException(
                    "le bureau entrant doit au minimum comporter un président et un trésorier");
        }
        p.marquerBureauComplete();
        return p;
    }

    /**
     * Étape 4 — l'investiture, à l'AG.
     *
     * <p>Une seule transaction : clore le sortant puis investir l'entrant. La
     * contrainte d'exclusion GiST garantit qu'aucun chevauchement ne peut
     * exister ; si les dates sont incohérentes, c'est la base qui refuse.
     */
    @Transactional
    public Passation activer(UUID demandeur, UUID passationId, OffsetDateTime aLAg) {
        Passation p = passation(passationId);
        politique.exiger(demandeur, Permission.PASSATION_LANCER, p.getAssociationId());

        if (p.getMandatSortantId() != null) {
            mandats.findById(p.getMandatSortantId()).ifPresent(sortant -> {
                if (sortant.getStatut() == StatutMandat.EN_FONCTION) {
                    sortant.clore(aLAg);
                }
            });
            mandats.flush();   // libère l'index « un seul mandat en fonction »
        }

        Mandat entrant = mandats.findById(p.getMandatEntrantId())
                .orElseThrow(() -> new IllegalStateException("mandat entrant introuvable"));
        entrant.investir(aLAg);

        p.activer(aLAg);
        return p;
    }

    /**
     * Les passations d'une association, la plus récente d'abord.
     *
     * <p>Aucune lecture n'existait : quatre routes faisaient AVANCER une
     * passation et aucune ne permettait d'en connaître l'état. Le dépôt portait
     * pourtant déjà le finder — {@code findByAssociationIdOrderByPrepareeLeDesc},
     * écrit et jamais appelé. Un écran ne peut pas proposer « désigner » ou
     * « activer » sans savoir où en est la passation ; c'est ce qui manquait
     * pour qu'elle soit autre chose qu'une suite d'appels curl.
     *
     * <p>Lecture réservée au bureau en fonction : la composition d'un bureau
     * ENTRANT avant son annonce en assemblée générale n'est pas publique, et
     * cette liste y mène.
     */
    @Transactional(readOnly = true)
    public List<Passation> passationsDe(UUID demandeur, UUID associationId) {
        politique.exiger(demandeur, Permission.PASSATION_LANCER, associationId);
        return passations.findByAssociationIdOrderByPrepareeLeDesc(associationId);
    }

    /** Tant qu'elle n'est pas activée, une passation ratée est annulable. */
    @Transactional
    public void annuler(UUID demandeur, UUID passationId) {
        Passation p = passation(passationId);
        politique.exiger(demandeur, Permission.PASSATION_LANCER, p.getAssociationId());
        p.annuler();
        mandats.findById(p.getMandatEntrantId()).ifPresent(m -> {
            if (m.getStatut() == StatutMandat.PREPARATION) {
                mandats.delete(m);      // cascade : pages et blocs du brouillon
            }
        });
    }

    /**
     * Désigner quelqu'un n'a de sens que tant que la passation est en cours.
     *
     * <p>Sans cette garde, la même route servait à deux choses très
     * différentes : composer le bureau entrant avant l'AG, et — une fois la
     * passation ACTIVEE — ajouter des membres à un bureau déjà investi, en
     * contournant les règles de gouvernance. Sur une passation ANNULEE, le
     * mandat entrant n'existe plus : l'appel produisait une violation de
     * contrainte remontée en 500.
     */
    private static void exigerOuverte(Passation p) {
        if (p.getStatut() != Passation.StatutPassation.PREPAREE
                && p.getStatut() != Passation.StatutPassation.BUREAU_COMPLETE) {
            throw new Erreurs.Conflit(
                    "cette passation est " + p.getStatut()
                    + " : on ne peut plus y désigner de membre");
        }
    }

    private Passation passation(UUID id) {
        return passations.findById(id)
                .orElseThrow(() -> new Erreurs.Introuvable("passation", id));
    }
}
