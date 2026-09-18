import type { Theme } from './types'

/**
 * Traduit les jetons de thème d'un mandat en variables CSS.
 *
 * <p>C'est le mécanisme par lequel chaque association a une page différente
 * sans une ligne de code spécifique — et par lequel une archive garde
 * l'apparence de son année, puisque le thème appartient au mandat.
 *
 * <p>Les jetons sont des CHOIX ENCADRÉS, pas du CSS libre. Le but produit est
 * « chaque asso est différente et aucune n'est cassée » ; laisser un bureau
 * étudiant écrire du CSS donne exactement l'inverse.
 */
const COULEUR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i
const RAYON = /^(?:0|\d{1,2}(?:\.\d)?)(?:px|rem)$/

/**
 * Une valeur de jeton n'est retenue que si elle a la FORME attendue.
 *
 * <p>Ces valeurs viennent de la base, donc d'un bureau étudiant, et
 * atterrissent telles quelles dans des variables CSS que la feuille de style
 * utilise en `background` et en `color`. Rien ne les vérifiait. Un jeton valant
 * `url(https://tiers.example/pixel.png)` était donc posé en variable, consommé
 * par une règle `background`, et CHAQUE visiteur de la page publique émettait
 * une requête vers ce tiers — un mouchard installé par le contenu, sans une
 * ligne de code, et sans CSP pour l'arrêter.
 *
 * <p>Le contrôle est ici, au point d'usage, et pas seulement à l'écriture :
 * c'est le dernier endroit traversé par toutes les valeurs, y compris celles
 * déjà en base et celles écrites par une future route.
 */
function valide(valeur: string | undefined, forme: RegExp, defaut: string): string {
  return typeof valeur === 'string' && forme.test(valeur.trim()) ? valeur.trim() : defaut
}

export function variablesDuTheme(theme: Theme): React.CSSProperties {
  const police =
    theme.police === 'SERIF'
      ? '"Iowan Old Style", "Palatino Linotype", Palatino, Georgia, serif'
      : 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif'

  return {
    '--accent': valide(theme.accent, COULEUR, '#1F4E79'),
    '--accent-contraste': valide(theme.accentContraste, COULEUR, '#FFFFFF'),
    '--encre': valide(theme.encre, COULEUR, '#101820'),
    '--fond': valide(theme.fond, COULEUR, '#F6F7F9'),
    '--rayon': valide(theme.rayon, RAYON, '8px'),
    '--police': police,
  } as React.CSSProperties
}
