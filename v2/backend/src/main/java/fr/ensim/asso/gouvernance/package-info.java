/**
 * Gouvernance — l'ossature temporelle : associations, années, mandats,
 * bureaux. Autorité unique pour « qui peut faire quoi, pour quelle
 * association, à quelle date ».
 *
 * <p>Ne dépend d'aucun autre module métier : c'est la couche dont tout le
 * reste dérive ses droits.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Gouvernance",
        allowedDependencies = {"shared"})
package fr.ensim.asso.gouvernance;
