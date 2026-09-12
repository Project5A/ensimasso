/**
 * Portail — le chemin de lecture public.
 *
 * <p>Module d'orchestration, comme {@code passation} : rendre une page publique
 * compose le contenu (blocs), la gouvernance (le bureau du mandat de la page)
 * et la médiathèque (résolution des URL). Le garder ici plutôt que de laisser
 * {@code contenu} appeler {@code media} évite un cycle et garde la frontière
 * vérifiable à la compilation.
 *
 * <p>C'est aussi le module que sert le profil {@code delivery} : seul chemin de
 * lecture, plus petit jeu de secrets, rôle PostgreSQL en lecture seule.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Portail public",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "contenu :: domain", "contenu :: app",
            "media :: domain", "media :: app",
            "agenda :: domain", "agenda :: app",
            "partenariat :: domain", "partenariat :: app",
            "shared"})
package fr.ensim.asso.portail;
