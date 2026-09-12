/**
 * Passation — le seul module d'orchestration.
 *
 * <p>Il dépend de {@code gouvernance} et de {@code contenu} parce que la
 * passation les touche ensemble, dans une seule transaction. C'est une
 * dépendance assumée et déclarée : la maintenir ici, plutôt que de laisser
 * gouvernance appeler contenu, évite un cycle entre modules métier et garde la
 * frontière vérifiable à la compilation.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Passation",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "contenu :: app",
            "shared"})
package fr.ensim.asso.passation;
