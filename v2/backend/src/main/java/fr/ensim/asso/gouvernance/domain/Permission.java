package fr.ensim.asso.gouvernance.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Les actions protégées de la plateforme, et les postes qui les portent.
 *
 * <p>Contrairement à la v1 — où l'autorisation n'existait que sous forme d'une
 * liste de motifs d'URL dans {@code SecurityConfig}, ce qui a laissé
 * {@code /api/users/**} entièrement public — la règle vit dans le domaine et
 * se teste comme une table de vérité.
 */
public enum Permission {

    PAGE_EDITER      (EnumSet.of(Poste.PRESIDENT, Poste.VICE_PRESIDENT, Poste.RESP_COM)),
    PAGE_PUBLIER     (EnumSet.of(Poste.PRESIDENT, Poste.VICE_PRESIDENT)),
    THEME_EDITER     (EnumSet.of(Poste.PRESIDENT, Poste.VICE_PRESIDENT, Poste.RESP_COM)),
    MEMBRE_GERER     (EnumSet.of(Poste.PRESIDENT, Poste.SECRETAIRE)),
    EVENEMENT_GERER  (EnumSet.of(Poste.PRESIDENT, Poste.RESP_EVENEMENTS)),
    // Un partenariat engage l'association : le président, celui qui encaisse,
    // et celui qui affiche le logo. Pas le responsable évènements, qui n'est
    // partie à aucune des deux contreparties.
    PARTENAIRE_GERER (EnumSet.of(Poste.PRESIDENT, Poste.TRESORIER, Poste.RESP_COM)),
    FINANCE_CONSULTER(EnumSet.of(Poste.PRESIDENT, Poste.TRESORIER)),
    CAMPAGNE_GERER   (EnumSet.of(Poste.PRESIDENT, Poste.TRESORIER)),
    MEDIA_DEPOSER    (EnumSet.of(Poste.PRESIDENT, Poste.VICE_PRESIDENT, Poste.RESP_COM,
                                 Poste.RESP_EVENEMENTS)),
    MEDIA_SUPPRIMER  (EnumSet.of(Poste.PRESIDENT, Poste.RESP_COM)),
    PASSATION_LANCER (EnumSet.of(Poste.PRESIDENT));

    private final Set<Poste> postes;

    Permission(Set<Poste> postes) { this.postes = postes; }

    public boolean portéePar(Poste poste) { return postes.contains(poste); }

    public Set<Poste> postesAutorises() { return Set.copyOf(postes); }
}
