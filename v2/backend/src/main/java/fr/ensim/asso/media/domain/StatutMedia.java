package fr.ensim.asso.media.domain;

public enum StatutMedia {
    /** Clé réservée, URL de dépôt émise, objet pas encore confirmé. */
    ATTENTE_DEPOT,
    /** Objet présent et vérifié : utilisable dans une page. */
    DISPONIBLE,
    /** Refusé à la vérification (type réel non conforme, trop volumineux). */
    REJETE,
    /** Retiré. Conservé en base pour que les archives restent explicables. */
    SUPPRIME
}
