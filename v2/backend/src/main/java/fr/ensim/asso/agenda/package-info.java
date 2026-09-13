/**
 * Agenda — les évènements organisés par un bureau.
 *
 * <p>Un évènement appartient à un <strong>mandat</strong>, pas à une
 * association. C'est la même règle que pour les pages, et elle a la même
 * conséquence heureuse : la page d'archive de 2023-2024 garde son agenda pour
 * toujours, sans qu'aucun code ne s'en occupe, et le bureau suivant ne peut pas
 * effacer l'histoire du précédent en faisant le ménage dans « ses » évènements.
 *
 * <p>Un évènement annulé n'est pas supprimé : il reste visible, marqué annulé,
 * avec son motif. Le faire disparaître laisserait sans réponse ceux qui
 * s'étaient inscrits — c'est une décision fonctionnelle, imposée ici par une
 * contrainte de base.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Agenda",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "media :: domain",
            "shared"})
package fr.ensim.asso.agenda;
