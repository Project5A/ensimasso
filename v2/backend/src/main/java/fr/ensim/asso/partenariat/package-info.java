/**
 * Partenariat — les partenaires et sponsors d'un bureau.
 *
 * <p>Rattachés au <strong>mandat</strong>, parce qu'un partenariat se
 * renégocie chaque année. C'est exactement le cas qui piège une modélisation
 * « par association » : le partenaire de 2023 réapparaîtrait tout seul sur la
 * page de 2026, avec son logo et sa mention « partenaire officiel », sans que
 * personne n'ait rien signé.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Partenariat",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "shared"})
package fr.ensim.asso.partenariat;
