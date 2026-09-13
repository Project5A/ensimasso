package fr.ensim.asso.adhesion.app;

import fr.ensim.asso.adhesion.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    private final CampagneAdhesionRepository campagnes;
    private final TarifAdhesionRepository tarifs;
    private final AdhesionRepository adhesions;
    private final MandatRepository mandats;
    private final AnneeUniversitaireRepository annees;
    private final PolitiqueAcces politique;
    private final Clock horloge;

    public ServiceAdhesion(CampagneAdhesionRepository campagnes,
                           TarifAdhesionRepository tarifs,
                           AdhesionRepository adhesions,
                           MandatRepository mandats,
                           AnneeUniversitaireRepository annees,
                           PolitiqueAcces politique,
                           Clock horloge) {
        this.campagnes = campagnes;
        this.tarifs = tarifs;
        this.adhesions = adhesions;
        this.mandats = mandats;
        this.annees = annees;
        this.politique = politique;
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
    public Adhesion adherer(UUID personneId, UUID campagneId, PublicCible cible) {
        CampagneAdhesion campagne = campagne(campagneId);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);

        if (!campagne.accepteAdhesionA(maintenant)) {
            throw new Erreurs.Conflit("cette campagne d'adhésion n'est pas ouverte");
        }

        adhesions.findByPersonneIdAndAssociationIdAndCouvreAnneeCode(
                        personneId, campagne.getAssociationId(), campagne.getCouvreAnneeCode())
                .ifPresent(existante -> {
                    throw new Erreurs.Conflit("vous avez déjà une adhésion pour "
                            + campagne.getCouvreAnneeCode() + " (statut : " + existante.getStatut() + ")");
                });

        // LE point de la méthode : le prix vient d'ici, jamais de l'appelant.
        TarifAdhesion tarif = tarifs.findByCampagneIdAndPublicCible(campagneId, cible)
                .orElseThrow(() -> new Erreurs.RequeteInvalide(
                        "aucun tarif « " + cible + " » pour cette campagne"));

        return adhesions.save(new Adhesion(
                personneId,
                campagne.getAssociationId(),
                campagne.getCouvreAnneeCode(),
                campagne.getOuvertePparMandatId(),
                tarif.getId(),
                tarif.getMontantCents()));
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

    @Transactional(readOnly = true)
    public List<TarifAdhesion> tarifsDe(UUID campagneId) {
        return tarifs.findByCampagneId(campagneId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<String> anneeCouranteCode() {
        return anneeCourante();
    }

    // ------------------------------------------------------------- interne

    private java.util.Optional<String> anneeCourante() {
        return annees.anneeCouvrant(LocalDate.now(horloge))
                .map(AnneeUniversitaire::getCode);
    }

    private CampagneAdhesion campagne(UUID id) {
        return campagnes.findById(id)
                .orElseThrow(() -> new Erreurs.Introuvable("campagne", id));
    }
}
