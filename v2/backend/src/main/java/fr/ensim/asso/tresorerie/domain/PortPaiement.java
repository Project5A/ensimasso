package fr.ensim.asso.tresorerie.domain;

/**
 * Le prestataire de paiement, vu par le domaine.
 *
 * <p>Le SDK n'apparaît que dans l'adaptateur. Deux conséquences concrètes :
 * la trésorerie est testable sans réseau, et remplacer Stripe par un autre
 * prestataire ne touche pas une ligne de logique métier.
 */
public interface PortPaiement {

    /**
     * Crée une intention de paiement. Le montant est celui calculé par le
     * serveur — c'est le paramètre qui, dans la v1, venait du navigateur.
     */
    Intention creerIntention(int montantCents, String devise, String referenceCommande);

    /** Vérifie la signature d'un webhook et en extrait l'évènement. */
    EvenementRecu verifierEtLire(String charge, String signature);

    void rembourser(String referencePaiement, int montantCents);

    record Intention(String reference, String secretClient) { }

    /**
     * Un évènement de paiement, réduit à ce dont le domaine a besoin.
     * {@code montantCents} est le montant <em>réellement encaissé</em> chez le
     * prestataire — comparé au montant dû, jamais supposé égal.
     */
    record EvenementRecu(String id, String type, String referencePaiement,
                         String referenceCommande, int montantCents, String devise) { }

    /** Levée si la signature est absente, malformée ou invalide. */
    class SignatureInvalideException extends RuntimeException {
        public SignatureInvalideException(String message) { super(message); }
    }
}
