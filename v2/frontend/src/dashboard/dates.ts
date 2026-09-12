/**
 * Conversion entre `<input type="datetime-local">` et l'ISO-8601 zoné attendu
 * par l'API.
 *
 * <p>Le champ HTML ne porte AUCUN fuseau : « 2026-10-03T20:00 » signifie
 * vingt heures pour la personne qui tape, et rien d'autre. L'API attend un
 * instant, donc un décalage. Faire la conversion à la main — coller un « Z »,
 * découper la chaîne — décale l'évènement d'une ou deux heures selon la saison,
 * et personne ne s'en aperçoit avant qu'un gala soit annoncé à 22 h.
 */

/** ISO zoné → valeur de champ, en heure locale du navigateur. */
export function isoVersChamp(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
    + `T${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * Valeur de champ → ISO zoné.
 *
 * <p>`new Date('2026-10-03T20:00')` — sans décalage — est interprété en heure
 * LOCALE par la spécification, ce qui est exactement ce que veut dire le champ.
 */
export function champVersIso(valeur: string): string | null {
  if (!valeur) return null
  const d = new Date(valeur)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}
