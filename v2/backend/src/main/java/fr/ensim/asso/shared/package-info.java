/**
 * Briques transverses : sécurité, gestion d'erreurs, configuration.
 *
 * <p>Module <em>ouvert</em> : ses sous-paquets sont volontairement accessibles
 * à tous les modules métier, parce que c'est précisément sa raison d'être. En
 * contrepartie, il ne dépend d'aucun module métier — les types d'erreur
 * partagés vivent ici, et les modules s'y conforment, jamais l'inverse.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package fr.ensim.asso.shared;
