package fr.ensim.asso.adhesion.app;

import fr.ensim.asso.adhesion.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Campagnes d'adhésion et adhésions.
 *
 * <p>Règle non négociable, et raison d'être de ce service : <strong>le prix
 * n'est jamais accepté depuis le client</strong>. L'appelant choisit un public
 * cible ; le montant est lu dans {@code tarif_adhesion}, côté serveur. La v1
 * lisait {@code amount} dans le corps de la requête et le passait tel quel à
 * Stripe, ce qui permettait de payer un centime pour n'importe quoi.
 */
@Service
public class ServiceAdhesion {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ServiceAdhesion.class);

    private final CampagneAdhesionRepository campagnes;
    private final TarifAdhesionRepository tarifs;
    private final AdhesionRepository adhesions;
    private final MandatRepository mandats;
    private final AnneeUniversitaireRepository annees;
    private final PolitiqueAcces politique;
    private final MeterRegistry metriques;
    private final Clock horloge;

    public ServiceAdhesion(CampagneAdhesionRepository campagnes,
                           TarifAdhesionRepository tarifs,
                           AdhesionRepository adhesions,
                           MandatRepository mandats,
                           AnneeUniversitaireRepository annees,
                           PolitiqueAcces politique,
                           MeterRegistry metriques,
                           Clock horloge) {
        this.campagnes = campagnes;
        this.tarifs = tarifs;
        this.adhesions = adhesions;
        this.mandats = mandats;
        this.annees = annees;
        this.politique = politique;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    // ----------------------------------------------------------- campagnes

    /**
     * Ouvre une campagne pour une année donnée. L'année couverte peut être
     * postérieure à l'année en cours : c'est précisément le cas « early bird ».
     */
    @Transactional
    public CampagneAdhesion ouvrirCampagne(UUID demandeur, UUID associationId,
                                           String couvreAnneeCode,
                                           OffsetDateTime fermeture) {
        politique.exiger(demandeur, Permission.CAMPAGNE_GERER, associationId);

        annees.findById(couvreAnneeCode).orElseThrow(() ->
                new Erreurs.RequeteInvalide("année universitaire inconnue : " + couvreAnneeCode));

        Mandat vendeur = mandats.mandatEnFonction(associationId).orElseThrow(() ->
                new Erreurs.Conflit("aucun bureau en fonction : impossible d'ouvrir une campagne"));

        // Une campagne par (association, année) : rappeler cette méthode sur une
        // campagne existante en rouvre la fenêtre, il n'en naît pas une seconde.
        // `ouverte_par_mandat_id` garde alors le bureau CRÉATEUR, et c'est
        // voulu : la colonne est immuable et enregistre qui a ouvert, pas qui a
        // rouvert en dernier. Ce qui doit suivre le bureau du jour, c'est
        // l'attribution de chaque VENTE, et elle est lue à la vente — voir
        // `adherer`. Tant qu'elle ne l'était pas, cette ligne figée décidait
        // aussi de la comptabilité, ce qu'elle n'a jamais eu à faire.
        CampagneAdhesion campagne = campagnes
                .findByAssociationIdAndCouvreAnneeCode(associationId, couvreAnneeCode)
                .orElseGet(() -> campagnes.save(
                        new CampagneAdhesion(associationId, couvreAnneeCode, vendeur.getId())));

        campagne.ouvrir(OffsetDateTime.now(horloge), fermeture);
        return campagne;
    }

    @Transactional
    public TarifAdhesion definirTarif(UUID demandeur, UUID campagneId, String libelle,
                                      int montantCents, PublicCible cible) {
        CampagneAdhesion campagne = campagne(campagneId);
        politique.exiger(demandeur, Permission.CAMPAGNE_GERER, campagne.getAssociationId());

        tarifs.findByCampagneIdAndPublicCible(campagneId, cible).ifPresent(t -> {
            throw new Erreurs.Conflit("un tarif « " + cible + " » existe déjà pour cette campagne");
        });
        return tarifs.save(new TarifAdhesion(campagneId, libelle, montantCents, cible));
    }

    @Transactional
    public void fermerCampagne(UUID demandeur, UUID campagneId) {
        CampagneAdhesion campagne = campagne(campagneId);
        politique.exiger(demandeur, Permission.CAMPAGNE_GERER, campagne.getAssociationId());
        campagne.fermer(OffsetDateTime.now(horloge));
    }

    // ----------------------------------------------------------- adhésions

    /**
     * Crée une adhésion pour la personne courante.
     *
     * <p>Le montant provient du tarif serveur. Si la campagne est gratuite
     * (tarif à 0), l'adhésion est active immédiatement ; sinon elle reste en
     * attente jusqu'à confirmation du paiement par le module trésorerie.
     */
    @Transactional
    public Adhesion adherer(UUID personneId, Set<String> rolesVerifies,
                            UUID campagneId, PublicCible cible) {
        return adherer(personneId, rolesVerifies, campagneId, cible, false);
    }

    /**
     * Adhérer, en sachant si un encaissement suit.
     *
     * <p>Cette distinction manquait, et elle laissait une trappe ouverte. Deux
     * routes créent une adhésion : {@code POST /adhesions/campagnes/{id}/adherer}
     * et {@code POST /tresorerie/commandes/adhesion}. Seule la seconde crée la
     * commande et l'intention de paiement. Sur une campagne PAYANTE, la
     * première produisait donc une adhésion EN_ATTENTE_PAIEMENT sans commande :
     * <ul>
     *   <li>elle ne peut pas être payée — il n'existe aucune commande, donc
     *       aucun secret client à demander ;</li>
     *   <li>elle occupe la place de l'année (index partiel
     *       {@code adhesion_une_vivante_par_annee}, V12) ;</li>
     *   <li>et elle ne peut pas être abandonnée : le seul chemin d'abandon part
     *       d'une commande, et il n'y en a pas.</li>
     * </ul>
     * L'étudiant était donc exclu de son association pour l'année, sans aucun
     * recours — exactement le verrou que V12 et le chemin d'abandon ont refermé
     * ailleurs, rouvert ici par une troisième porte.
     *
     * <p>La route publique refuse désormais un tarif payant et dit où aller.
     * Elle reste la bonne porte pour une adhésion GRATUITE, qui s'active
     * immédiatement et n'a rien à encaisser.
     *
     * @param paiementPrisEnCharge l'appelant crée-t-il la commande qui suit ?
     */
    @Transactional
    public Adhesion adherer(UUID personneId, Set<String> rolesVerifies,
                            UUID campagneId, PublicCible cible,
                            boolean paiementPrisEnCharge) {
        // Choisir son public, c'est choisir son prix. Le corps de la requête ne
        // contient aucun montant — c'était la correction de la faille PAY de la
        // v1 — mais il contenait le sélecteur qui le détermine, et rien ne
        // vérifiait que l'appelant avait droit au tarif réclamé. Un extérieur
        // demandait le tarif étudiant et le payait.
        if (!cible.estOuvertA(rolesVerifies)) {
            throw new Erreurs.AccesRefuse(
                    "le tarif « " + cible + " » demande le statut "
                  + cible.roleRequis().orElse("") + ", que votre compte ne porte pas");
        }
        CampagneAdhesion campagne = campagne(campagneId);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);

        if (!campagne.accepteAdhesionA(maintenant)) {
            throw new Erreurs.Conflit("cette campagne d'adhésion n'est pas ouverte");
        }

        // Seule une adhésion VIVANTE fait obstacle. La recherche portait sur
        // tous les statuts : une adhésion REMBOURSEE fermait donc l'année à
        // l'étudiant définitivement, alors que le remboursement venait
        // justement de lui reprendre son droit. Voir V12.
        adhesions.adhesionVivante(personneId, campagne.getAssociationId(),
                        campagne.getCouvreAnneeCode())
                .ifPresent(vivante -> {
                    // Le message nomme le statut réellement lu, et n'en suppose
                    // aucun : la requête n'en rend que deux aujourd'hui, mais un
                    // message qui décrirait le mauvais cas serait pire qu'un
                    // message générique.
                    throw new Erreurs.Conflit(switch (vivante.getStatut()) {
                        case ACTIVE -> "vous êtes déjà adhérent pour "
                                + campagne.getCouvreAnneeCode();
                        case EN_ATTENTE_PAIEMENT -> "une adhésion pour "
                                + campagne.getCouvreAnneeCode()
                                + " attend déjà son paiement : reprenez-la plutôt que d'en "
                                + "ouvrir une seconde";
                        default -> "une adhésion pour " + campagne.getCouvreAnneeCode()
                                + " occupe déjà la place (statut : " + vivante.getStatut() + ")";
                    });
                });

        // LE point de la méthode : le prix vient d'ici, jamais de l'appelant.
        TarifAdhesion tarif = tarifs.findByCampagneIdAndPublicCible(campagneId, cible)
                .orElseThrow(() -> new Erreurs.RequeteInvalide(
                        "aucun tarif « " + cible + " » pour cette campagne"));

        // Le bureau qui ENCAISSE, lu MAINTENANT — c'est ce que la colonne dit
        // d'elle-même : « vendue_par_mandat_id — l'audit : quel bureau a
        // encaissé ». Le service y écrivait pourtant le bureau qui avait OUVERT
        // la campagne. Les deux ne coïncident que si aucune passation n'a lieu
        // entre l'ouverture et la vente, c'est-à-dire dans tous les cas SAUF
        // celui pour lequel cette colonne existe : une campagne « early bird »
        // ouverte en juillet par le bureau sortant reste ouverte à la rentrée,
        // et chaque adhésion vendue en septembre était portée au compte d'un
        // bureau qui n'était plus en fonction.
        Mandat vendeur = mandats.mandatEnFonction(campagne.getAssociationId()).orElseThrow(() ->
                new Erreurs.Conflit("aucun bureau en fonction : personne ne peut encaisser "
                                  + "cette adhésion"));

        if (tarif.getMontantCents() > 0 && !paiementPrisEnCharge) {
            throw new Erreurs.Conflit(
                    "ce tarif est payant : passez par la commande d'adhésion, qui crée "
                  + "l'intention de paiement. Une adhésion créée ici ne pourrait ni être "
                  + "payée ni être annulée.");
        }

        return adhesions.save(new Adhesion(
                personneId,
                campagne.getAssociationId(),
                campagne.getCouvreAnneeCode(),
                vendeur.getId(),
                tarif.getId(),
                tarif.getMontantCents(),
                maintenant));
    }

    /**
     * Confirme le paiement d'une adhésion.
     *
     * <p>Appelé par le module trésorerie <em>dans la même transaction</em> que
     * l'enregistrement du paiement. C'est tout l'intérêt de garder trésorerie
     * comme module et non comme service : {@code commande} et {@code adhesion}
     * valident ensemble, et « tout paiement encaissé a-t-il bien donné un
     * droit ? » reste une jointure, pas un script de rapprochement nocturne.
     *
     * <p>Idempotente : rejouer le même évènement de paiement est sans effet.
     */
    @Transactional
    public Adhesion confirmerPaiement(UUID adhesionId, String paiementRef) {
        Adhesion adhesion = adhesions.findById(adhesionId)
                .orElseThrow(() -> new Erreurs.Introuvable("adhésion", adhesionId));

        adhesions.findByPaiementRef(paiementRef).ifPresent(autre -> {
            if (!autre.getId().equals(adhesionId)) {
                throw new Erreurs.Conflit(
                        "cette référence de paiement a déjà activé une autre adhésion");
            }
        });

        adhesion.activer(paiementRef, OffsetDateTime.now(horloge));
        return adhesion;
    }

    /**
     * Reprend le droit acheté lorsque la commande est remboursée.
     *
     * <p>{@code Adhesion.rembourser()} existait depuis le premier jour et
     * n'était appelée de nulle part : la trésorerie remboursait l'argent,
     * écrivait la sortie au journal, passait la commande en REMBOURSEE — et
     * laissait l'adhésion ACTIVE. L'association rendait l'argent et gardait
     * l'adhérent.
     *
     * <p>Idempotente sur une adhésion déjà remboursée : rembourser deux fois
     * est une erreur d'exploitation, pas une raison de casser la transaction.
     */
    @Transactional
    public Adhesion revoquerPourRemboursement(UUID adhesionId) {
        Adhesion adhesion = adhesions.findById(adhesionId)
                .orElseThrow(() -> new Erreurs.Introuvable("adhésion", adhesionId));
        if (adhesion.estActive()) {
            adhesion.rembourser();
        }
        return adhesion;
    }

    /**
     * Abandonne une adhésion dont le paiement n'aboutira pas.
     *
     * <p>{@code Adhesion.annuler()} existait depuis le premier jour sans aucun
     * appelant, exactement comme {@code rembourser()} avant elle : le statut
     * ANNULEE était inatteignable en production. La conséquence n'était pas
     * cosmétique. Une adhésion dont le paiement échouait restait
     * EN_ATTENTE_PAIEMENT pour toujours — aucune tâche ne l'expire, aucune
     * route ne l'annulait — et l'index partiel
     * {@code adhesion_une_vivante_par_annee} la compte comme vivante. La
     * personne était donc exclue de cette association pour l'année entière,
     * par la même porte que celle refermée pour les remboursements, et sans
     * autre recours qu'un DELETE à la main en base.
     *
     * <p>Idempotente : abandonner deux fois n'est pas une erreur. Une adhésion
     * ACTIVE, elle, refuse — celle-là se rembourse.
     */
    @Transactional
    public Adhesion abandonner(UUID adhesionId) {
        Adhesion adhesion = adhesions.findById(adhesionId)
                .orElseThrow(() -> new Erreurs.Introuvable("adhésion", adhesionId));
        if (adhesion.getStatut() == StatutAdhesion.ANNULEE) {
            return adhesion;
        }
        adhesion.annuler();
        return adhesion;
    }

    // ------------------------------------------------------------ lectures

    /**
     * Cette personne est-elle adhérente en ce moment ?
     *
     * <p>Aucune tâche d'expiration ne tourne : l'adhésion appartient à une
     * année, et l'année courante est déduite de la date. Un achat de juillet
     * pour l'année suivante n'est pas encore courant, et le devient tout seul.
     */
    @Transactional(readOnly = true)
    public boolean estAdherent(UUID personneId, UUID associationId) {
        return anneeCourante()
                .map(annee -> adhesions.estAdherent(personneId, associationId, annee))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Adhesion> mesAdhesions(UUID personneId) {
        return adhesions.adhesionsActivesDe(personneId);
    }

    @Transactional(readOnly = true)
    public List<Adhesion> adherentsDe(UUID demandeur, UUID associationId, String anneeCode) {
        // La liste des adhérents est une donnée nominative : elle n'est pas publique.
        politique.exiger(demandeur, Permission.CAMPAGNE_GERER, associationId);
        return adhesions.findByAssociationIdAndCouvreAnneeCode(associationId, anneeCode);
    }

    /**
     * Les campagnes d'une association, la plus récente d'abord.
     *
     * <p>Troisième finder écrit et jamais appelé du dépôt — après celui des
     * passations et celui des postes. Sans lui, rien ne permet de savoir si une
     * campagne est déjà ouverte : ni au bureau, qui en rouvrirait une seconde,
     * ni à l'étudiant, qui n'a aucun moyen de trouver l'identifiant de campagne
     * que la route d'adhésion exige.
     *
     * <p>Lisible par tout compte authentifié, comme les tarifs : l'existence
     * d'une campagne et son prix sont ce qu'une association AFFICHE.
     */
    @Transactional(readOnly = true)
    public List<CampagneAdhesion> campagnesDe(UUID associationId) {
        return campagnes.findByAssociationIdOrderByCouvreAnneeCodeDesc(associationId);
    }

    @Transactional(readOnly = true)
    public List<TarifAdhesion> tarifsDe(UUID campagneId) {
        return tarifs.findByCampagneId(campagneId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<String> anneeCouranteCode() {
        return anneeCourante();
    }

    // ------------------------------------------------------------- interne

    /**
     * L'année universitaire qui couvre aujourd'hui, s'il y en a une.
     *
     * <p>Quand il n'y en a pas, {@code estAdherent} répond « non » — à tout le
     * monde, et sans rien dire. Ce n'est pas une réponse : c'est une panne de
     * configuration qui a l'apparence d'une réponse. Le 1er septembre, si
     * personne n'a inscrit l'année suivante, tous les adhérents de toutes les
     * associations cessent d'en être, les portes du gala se ferment, et rien
     * dans les journaux ne distingue cela d'un non-adhérent ordinaire.
     *
     * <p>On ne peut pas lever : ce serait faire tomber le portail public pour
     * une ligne manquante dans un calendrier. On le dit donc, fort et à chaque
     * fois. Le bruit est délibéré — la condition dure jusqu'à ce qu'un humain
     * inscrive l'année, et elle prive TOUTE l'école de son adhésion pendant ce
     * temps-là.
     */
    private java.util.Optional<String> anneeCourante() {
        LocalDate jour = LocalDate.now(horloge);
        java.util.Optional<String> annee = annees.anneeCouvrant(jour)
                .map(AnneeUniversitaire::getCode);
        if (annee.isEmpty()) {
            log.warn("aucune année universitaire ne couvre le {} : toute vérification "
                   + "d'adhésion répondra « non ». Inscrivez l'année dans "
                   + "annee_universitaire.", jour);
            metriques.counter("ensimasso.adhesion.calendrier_absent").increment();
        }
        return annee;
    }

    private CampagneAdhesion campagne(UUID id) {
        return campagnes.findById(id)
                .orElseThrow(() -> new Erreurs.Introuvable("campagne", id));
    }
}
