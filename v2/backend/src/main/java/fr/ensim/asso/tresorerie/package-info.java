/**
 * Trésorerie — commandes, paiements, journal comptable.
 *
 * <p>Dépend d'{@code adhesion} parce que c'est ici qu'est accordé le droit
 * payé, <strong>dans la même transaction</strong> que l'enregistrement du
 * paiement. C'est la raison assumée de garder la trésorerie comme module et
 * non comme service séparé : à travers un réseau, « tout paiement encaissé
 * a-t-il bien donné un droit ? » cesse d'être une jointure pour devenir un
 * rapprochement nocturne — que personne ne lit après trois mois.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Trésorerie",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "adhesion :: domain", "adhesion :: app",
            "shared"})
package fr.ensim.asso.tresorerie;
