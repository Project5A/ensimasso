package fr.ensim.asso.gouvernance.domain;

public enum StatutMandat {
    /** Créé par la passation, invisible du public, période encore provisoire. */
    PREPARATION,
    /** Bureau en exercice. Au plus un par association, imposé par la base. */
    EN_FONCTION,
    /** Mandat terminé. Immuable : c'est ce qui rend l'archive fiable. */
    CLOS
}
