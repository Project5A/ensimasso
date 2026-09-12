/**
 * Médiathèque — dépôt, métadonnées et résolution d'URL des objets binaires.
 *
 * <p>Règle fondatrice du module : la base ne stocke <strong>que la clé</strong>
 * d'objet. L'URL est fabriquée à la lecture. La v1 persistait une URL signée
 * valable 24 h, ce qui fait mourir chaque image un jour après son dépôt.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Média",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "shared"})
package fr.ensim.asso.media;
