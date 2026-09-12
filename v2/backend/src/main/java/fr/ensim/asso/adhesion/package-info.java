/**
 * Adhésions — campagnes, tarifs, adhésions. Le prix vit ici, côté serveur.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Adhésions",
        allowedDependencies = {"gouvernance", "gouvernance :: domain", "shared"})
package fr.ensim.asso.adhesion;
