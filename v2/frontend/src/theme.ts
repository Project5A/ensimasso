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
export function variablesDuTheme(theme: Theme): React.CSSProperties {
  const police =
    theme.police === 'SERIF'
      ? '"Iowan Old Style", "Palatino Linotype", Palatino, Georgia, serif'
      : 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif'

  return {
    '--accent': theme.accent ?? '#1F4E79',
    '--accent-contraste': theme.accentContraste ?? '#FFFFFF',
    '--encre': theme.encre ?? '#101820',
    '--fond': theme.fond ?? '#F6F7F9',
    '--rayon': theme.rayon ?? '8px',
    '--police': police,
  } as React.CSSProperties
}
