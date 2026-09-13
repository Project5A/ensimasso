package fr.ensim.asso.adhesion.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Les publics auxquels une association propose un tarif distinct.
 *
 * <p>Choisir son public, c'est choisir son prix. Le corps de la requête
 * d'adhésion ne contient volontairement aucun montant — mais il contenait le
 * sélecteur qui le détermine, et personne ne vérifiait que l'appelant avait
 * droit au tarif qu'il réclamait. Un extérieur demandait le tarif étudiant et
 * le payait. C'est la faille PAY de la v1 — {@code amount} lu dans le corps —
 * revenue par un chemin plus discret.
 *
 * <p>L'éligibilité se lit dans le jeton vérifié, et c'est le seul endroit où
 * elle peut se lire : « est étudiant » est une propriété que l'annuaire de
 * l'école détient et que cette application ne peut pas deviner.
 */
public enum PublicCible {

    ETUDIANT("STUDENT"),
    EXTERIEUR(null),
    ANCIEN("ALUMNI");

    private final String roleRequis;

    PublicCible(String roleRequis) { this.roleRequis = roleRequis; }

    /** Le rôle global exigé pour prétendre à ce public, s'il y en a un. */
    public Optional<String> roleRequis() { return Optional.ofNullable(roleRequis); }

    /**
     * EXTERIEUR n'exige rien : c'est le public par défaut, et le tarif le plus
     * élevé. Se déclarer extérieur quand on est étudiant est un choix qui
     * coûte plus cher, pas une fraude.
     */
    public boolean estOuvertA(Set<String> rolesVerifies) {
        return roleRequis().map(rolesVerifies::contains).orElse(true);
    }
}
