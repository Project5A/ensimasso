package fr.ensim.asso.partenariat.domain;

/**
 * Le niveau d'un partenariat, du plus engageant au plus léger.
 *
 * <p>L'ordre de déclaration est l'ordre d'affichage : un partenaire « or »
 * passe avant un « soutien ». Le tri n'a donc pas besoin d'une colonne de
 * rang, et il ne peut pas diverger du sens des niveaux.
 */
public enum NiveauPartenaire {
    OR,
    ARGENT,
    BRONZE,
    SOUTIEN
}
