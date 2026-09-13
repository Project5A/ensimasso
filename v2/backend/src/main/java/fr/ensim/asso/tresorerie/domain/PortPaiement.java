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

    /**
     * Le secret client d'une intention DÉJÀ créée.
     *
     * <p>Distinct de {@link #creerIntention} et pas par cosmétique : redemander
     * un secret ne doit pas créer une nouvelle intention facturable. Rendu vide
     * si l'intention n'existe plus chez le prestataire.
     */
    java.util.Optional<String> secretClientDe(String referenceIntention);

    /** Vérifie la signature d'un webhook et en extrait l'évènement. */
    EvenementRecu verifierEtLire(String charge, String signature);

    void rembourser(String referencePaiement, int montantCents);

    record Intention(String reference, String secretClient) { }

    /**
     * Un évènement de paiement, réduit à ce dont le domaine a besoin.
     * {@code montantCents} est le montant <em>réellement encaissé</em> chez le
     * prestataire — comparé au montant dû, jamais supposé égal.
     *
     * @param objetLisible l'objet de données a-t-il pu être lu ? Un évènement
     *        signé dont la charge utile n'est pas déchiffrable par le SDK — en
     *        pratique, un compte Stripe sur une version d'API différente — est
     *        un évènement qu'on n'a <em>pas compris</em>. Ce n'est pas la même
     *        chose qu'un évènement compris et sans intérêt pour nous, et le
     *        domaine doit pouvoir faire la différence : dans un cas on marque
     *        l'évènement traité, dans l'autre on refuse de l'acquitter.
     */
    record EvenementRecu(String id, String type, String referencePaiement,
                         String referenceCommande, int montantCents, String devise,
                         boolean objetLisible) {

        /** Évènement dont l'objet de données a été lu normalement. */
        public static EvenementRecu lu(String id, String type, String referencePaiement,
                                       String referenceCommande, int montantCents, String devise) {
            return new EvenementRecu(id, type, referencePaiement, referenceCommande,
                    montantCents, devise, true);
        }

        /** Évènement signé et identifié, mais dont l'objet reste illisible. */
        public static EvenementRecu illisible(String id, String type) {
            return new EvenementRecu(id, type, null, null, 0, null, false);
        }
    }

    /** Levée si la signature est absente, malformée ou invalide. */
    class SignatureInvalideException extends RuntimeException {
        public SignatureInvalideException(String message) { super(message); }
    }
}
