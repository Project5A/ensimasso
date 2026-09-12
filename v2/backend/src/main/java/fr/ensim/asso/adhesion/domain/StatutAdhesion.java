package fr.ensim.asso.adhesion.domain;

public enum StatutAdhesion {
    /** Créée, en attente de confirmation du paiement par le prestataire. */
    EN_ATTENTE_PAIEMENT,
    /** Payée et active pour l'année couverte. */
    ACTIVE,
    /** Annulée avant paiement, ou campagne fermée entre-temps. */
    ANNULEE,
    /** Remboursée après coup. */
    REMBOURSEE
}
