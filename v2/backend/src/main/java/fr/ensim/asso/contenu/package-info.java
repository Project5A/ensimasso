/**
 * Contenu — pages, versions, blocs, thèmes, registre des types de blocs.
 * Dépend de gouvernance uniquement pour vérifier les droits sur un mandat.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Contenu",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "shared"})
package fr.ensim.asso.contenu;
