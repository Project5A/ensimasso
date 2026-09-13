package fr.ensim.asso.tresorerie.app;

import fr.ensim.asso.adhesion.app.ServiceAdhesion;
import fr.ensim.asso.adhesion.domain.Adhesion;
import fr.ensim.asso.adhesion.domain.PublicCible;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.shared.error.Erreurs;
import fr.ensim.asso.tresorerie.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Commandes, encaissements et octroi du droit payé.
 *
 * <p>Ce service est la réponse aux trois failles PAY de l'audit. La plus
 * importante n'est pas une ligne de code mais une frontière : <strong>le
 * paiement et le droit qu'il achète sont validés dans la même transaction
 * locale.</strong> Réparti entre services, il faudrait une saga, une
 * compensation, et un rapprochement nocturne — et la revue d'architecture a
 * été catégorique : le budget réaliste d'un mainteneur seul est d'<em>un</em>
 * rapprochement, à dépenser sur Stripe, qui est distant et où l'asynchronisme
 * est inévitable. Tout le reste doit rester un COMMIT.
 */
@Service
public class ServiceTresorerie {

    private static final Logger log = LoggerFactory.getLogger(ServiceTresorerie.class);
    private static final String FOURNISSEUR = "STRIPE";

    private final CommandeRepository commandes;
    private final LigneCommandeRepository lignes;
    private final PaiementRepository paiements;
    private final EvenementStripeRepository evenements;
    private final EcritureLedgerRepository ledger;
    private final PortPaiement prestataire;
    private final ServiceAdhesion adhesions;
    private final PolitiqueAcces politique;
    private final Clock horloge;

    public ServiceTresorerie(CommandeRepository commandes, LigneCommandeRepository lignes,
                             PaiementRepository paiements, EvenementStripeRepository evenements,
                             EcritureLedgerRepository ledger, PortPaiement prestataire,
                             ServiceAdhesion adhesions, PolitiqueAcces politique, Clock horloge) {
        this.commandes = commandes;
        this.lignes = lignes;
        this.paiements = paiements;
        this.evenements = evenements;
        this.ledger = ledger;
        this.prestataire = prestataire;
        this.adhesions = adhesions;
        this.politique = politique;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------ commande

    /**
     * Crée la commande d'une adhésion et son intention de paiement.
     *
     * <p>Le client n'envoie <strong>aucun montant</strong> : il désigne une
     * campagne et un public cible. Le prix est lu dans les tarifs par le module
     * adhésion, la commande est totalisée ici, et c'est ce total-là qui part
     * chez Stripe.
     *
     * <p>Dans la v1, {@code POST /api/payment/create-payment-intent} lisait
     * {@code amount} dans le corps de la requête : poster {@code {"amount":1}}
     * suffisait à tout payer un centime.
     */
    @Transactional
    public Commande commanderAdhesion(UUID personneId, java.util.Set<String> rolesVerifies,
                                      UUID campagneId, PublicCible cible) {
        // Le module adhésion crée l'adhésion EN_ATTENTE_PAIEMENT au tarif
        // serveur — et vérifie que l'appelant a droit au public qu'il réclame.
        Adhesion adhesion = adhesions.adherer(personneId, rolesVerifies, campagneId, cible);

        if (lignes.existsByTypeLigneAndReferenceId(TypeLigne.ADHESION, adhesion.getId())) {
            throw new Erreurs.Conflit("cette adhésion est déjà rattachée à une commande");
        }

        int montant = adhesion.getMontantPayeCents();
        Commande commande = commandes.save(
                new Commande(personneId, adhesion.getAssociationId(), montant, "EUR"));

        lignes.save(new LigneCommande(commande.getId(), TypeLigne.ADHESION, adhesion.getId(),
                "Adhésion " + adhesion.getCouvreAnneeCode(), montant));

        if (montant > 0) {
            PortPaiement.Intention intention = prestataire.creerIntention(
                    montant, "EUR", commande.getId().toString());
            commande.rattacherIntention(intention.reference());
        } else {
            // Adhésion gratuite : déjà ACTIVE côté module adhésion, rien à encaisser.
            commande.marquerPayee(0, OffsetDateTime.now(horloge));
        }
        return commande;
    }

    /**
     * Le secret client de l'intention DÉJÀ créée pour cette commande.
     *
     * <p>Cette méthode créait une nouvelle intention à chaque appel. Trois
     * conséquences, toutes réelles : chaque rafraîchissement de la page de
     * paiement laissait une intention ouverte de plus chez Stripe, toutes
     * portant la même métadonnée de commande et donc toutes payables — une
     * commande pouvait être encaissée deux fois ; la commande ne pointait que
     * vers la première ; et une commande déjà payée ouvrait quand même un
     * nouveau moyen de la repayer. Le tout sous {@code readOnly = true}, qui
     * annonçait une lecture.
     */
    @Transactional(readOnly = true)
    public String secretClientDe(UUID personneId, UUID commandeId) {
        Commande c = commande(commandeId);
        if (!c.getPersonneId().equals(personneId)) {
            throw new Erreurs.AccesRefuse("cette commande n'est pas la vôtre");
        }
        if (c.getStatut() != StatutCommande.OUVERTE) {
            throw new Erreurs.Conflit(
                    "commande " + c.getStatut() + " : il n'y a plus rien à payer");
        }
        if (c.getIntentionRef() == null) {
            throw new Erreurs.Conflit("aucune intention de paiement pour cette commande");
        }
        // Le secret n'est pas stocké — c'est un jeton de courte durée, il n'a
        // rien à faire en base — mais il est RELU, pas refabriqué.
        return prestataire.secretClientDe(c.getIntentionRef())
                .orElseThrow(() -> new Erreurs.Conflit(
                        "l'intention de paiement de cette commande n'existe plus chez le prestataire"));
    }

    // ------------------------------------------------------------- webhook

    /**
     * Traite un évènement du prestataire. <strong>Une seule transaction.</strong>
     *
     * <p>Séquence : vérifier la signature (fait par l'appelant), déduplicquer
     * sur l'identifiant d'évènement, enregistrer le paiement, écrire au journal,
     * marquer la commande payée, puis accorder chaque droit acheté.
     *
     * <p>Si quoi que ce soit échoue, rien n'est écrit — pas d'état intermédiaire
     * « payé mais sans droit », qui est exactement le message que reçoit un
     * président : « j'ai payé et je n'ai rien reçu ».
     */
    @Transactional
    public ResultatWebhook traiter(PortPaiement.EvenementRecu evenement) {
        // Idempotence au niveau de la base : la clé primaire est l'identifiant
        // de l'évènement. Stripe réessaie ; un rejeu ne doit rien refaire.
        if (evenements.existsById(evenement.id())) {
            log.info("évènement {} déjà traité, rejeu ignoré", evenement.id());
            return ResultatWebhook.DEJA_TRAITE;
        }
        EvenementStripe trace = evenements.save(
                new EvenementStripe(evenement.id(), evenement.type()));

        if (!"payment_intent.succeeded".equals(evenement.type())) {
            trace.marquerTraite("ignoré : " + evenement.type(), OffsetDateTime.now(horloge));
            return ResultatWebhook.IGNORE;
        }

        // Un évènement dont l'objet n'a pas pu être lu n'est PAS un évènement
        // sans intérêt : c'est un évènement qu'on n'a pas compris. Le marquer
        // traité l'enterrait pour toujours — l'argent était encaissé chez
        // Stripe, le droit acheté n'était jamais accordé, et la déduplication
        // par identifiant d'évènement interdisait tout rejeu. Lever annule la
        // transaction, donc la ligne de déduplication elle-même : Stripe
        // réessaiera, et un humain verra passer l'échec.
        if (!evenement.objetLisible()) {
            throw new IllegalStateException(
                    "objet de données illisible pour l'évènement " + evenement.id()
                  + " : refus d'acquitter un paiement qu'on n'a pas su lire");
        }

        if (evenement.referenceCommande() == null) {
            trace.marquerTraite("aucune référence de commande", OffsetDateTime.now(horloge));
            return ResultatWebhook.IGNORE;
        }

        Commande commande = commandes.findById(UUID.fromString(evenement.referenceCommande()))
                .orElseThrow(() -> new Erreurs.Introuvable("commande", evenement.referenceCommande()));

        // Le montant encaissé est COMPARÉ au montant dû, jamais supposé égal.
        commande.marquerPayee(evenement.montantCents(), OffsetDateTime.now(horloge));

        Paiement paiement = paiements
                .findByFournisseurAndReference(FOURNISSEUR, evenement.referencePaiement())
                .orElseGet(() -> paiements.save(new Paiement(
                        commande.getId(), FOURNISSEUR, evenement.referencePaiement(),
                        evenement.montantCents(), evenement.devise().toUpperCase(),
                        Paiement.StatutPaiement.REUSSI)));

        ledger.save(new EcritureLedger(commande.getId(), paiement.getId(),
                commande.getAssociationId(), EcritureLedger.Sens.ENTREE,
                evenement.montantCents(), "Encaissement commande " + commande.getId()));

        // Et, dans la MÊME transaction, le droit acheté.
        accorderDroits(commande, paiement.getReference());

        trace.marquerTraite("commande " + commande.getId() + " payée", OffsetDateTime.now(horloge));
        return ResultatWebhook.TRAITE;
    }

    private void accorderDroits(Commande commande, String referencePaiement) {
        for (LigneCommande ligne : lignes.findByCommandeId(commande.getId())) {
            switch (ligne.getTypeLigne()) {
                case ADHESION -> adhesions.confirmerPaiement(ligne.getReferenceId(), referencePaiement);
                case BILLET -> throw new UnsupportedOperationException(
                        "la billetterie n'est pas encore implémentée : voir v2/README.md");
            }
        }
    }

    // -------------------------------------------------------- remboursement

    @Transactional
    public void rembourser(UUID demandeur, UUID commandeId, String motif) {
        Commande commande = commande(commandeId);
        politique.exiger(demandeur, Permission.FINANCE_CONSULTER, commande.getAssociationId());

        List<Paiement> encaissements = paiements.findByCommandeId(commandeId).stream()
                .filter(p -> p.getStatut() == Paiement.StatutPaiement.REUSSI)
                .toList();
        if (encaissements.isEmpty()) {
            throw new Erreurs.Conflit("aucun encaissement à rembourser sur cette commande");
        }

        for (Paiement p : encaissements) {
            prestataire.rembourser(p.getReference(), p.getMontantCents());
            p.marquerRembourse();
            ledger.save(new EcritureLedger(commande.getId(), p.getId(), commande.getAssociationId(),
                    EcritureLedger.Sens.SORTIE, p.getMontantCents(), "Remboursement : " + motif));
        }
        commande.rembourser();

        // Et, dans la MÊME transaction, le droit acheté est repris. Symétrique
        // de accorderDroits() : l'encaissement ouvre le droit, la restitution
        // le ferme. Sans cela, une adhésion intégralement remboursée restait
        // ACTIVE — l'association rendait l'argent et gardait l'adhérent, dont
        // la carte passait encore à l'entrée du gala.
        revoquerDroits(commande);
    }

    private void revoquerDroits(Commande commande) {
        for (LigneCommande ligne : lignes.findByCommandeId(commande.getId())) {
            switch (ligne.getTypeLigne()) {
                case ADHESION -> adhesions.revoquerPourRemboursement(ligne.getReferenceId());
                case BILLET -> throw new UnsupportedOperationException(
                        "la billetterie n'est pas encore implémentée : voir v2/README.md");
            }
        }
    }

    // ------------------------------------------------------------- lectures

    @Transactional(readOnly = true)
    public List<Commande> mesCommandes(UUID personneId) {
        return commandes.findByPersonneIdOrderByCreeLeDesc(personneId);
    }

    /** Le journal d'une association. Réservé au président et au trésorier. */
    @Transactional(readOnly = true)
    public List<EcritureLedger> journal(UUID demandeur, UUID associationId) {
        politique.exiger(demandeur, Permission.FINANCE_CONSULTER, associationId);
        return ledger.findByAssociationIdOrderByCreeLeDesc(associationId);
    }

    @Transactional(readOnly = true)
    public long solde(UUID demandeur, UUID associationId) {
        politique.exiger(demandeur, Permission.FINANCE_CONSULTER, associationId);
        return ledger.solde(associationId);
    }

    private Commande commande(UUID id) {
        return commandes.findById(id).orElseThrow(() -> new Erreurs.Introuvable("commande", id));
    }

    public enum ResultatWebhook { TRAITE, DEJA_TRAITE, IGNORE }
}
