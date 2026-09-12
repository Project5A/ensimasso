package fr.ensim.asso.agenda.domain;

/**
 * Le cycle de vie d'un évènement.
 *
 * <p>{@code ANNULE} est un état, pas une suppression : un évènement annulé
 * reste affiché avec son motif. C'est ce que demande la réalité — on annule
 * après avoir communiqué, donc après que des gens ont prévu de venir.
 */
public enum StatutEvenement {
    BROUILLON,
    PUBLIE,
    ANNULE
}
