/**
 * Adhésions — campagnes, tarifs, adhésions.
 *
 * <p>Le prix vit ici, côté serveur, et n'est jamais accepté depuis le client :
 * c'est la correction structurelle de la faille PAY-03 de la v1, où le montant
 * de l'intention de paiement Stripe était lu dans le corps de la requête.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Adhésions",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "shared"})
package fr.ensim.asso.adhesion;
