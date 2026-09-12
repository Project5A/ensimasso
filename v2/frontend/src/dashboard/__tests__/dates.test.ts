import { describe, expect, it } from 'vitest'
import { champVersIso, isoVersChamp } from '../dates'

describe('champs de date', () => {
  it('fait l’aller-retour sans décaler l’heure', () => {
    // Le piège : coller un « Z » sur la valeur du champ décalerait l'évènement
    // du décalage horaire du navigateur, l'été d'une heure de plus qu'en hiver.
    const champ = '2026-10-03T20:00'
    const iso = champVersIso(champ)
    expect(iso).not.toBeNull()
    expect(isoVersChamp(iso)).toBe(champ)
  })

  it('tient aussi de l’autre côté du changement d’heure', () => {
    for (const champ of ['2026-01-15T09:30', '2026-07-15T09:30', '2026-12-31T23:59']) {
      expect(isoVersChamp(champVersIso(champ))).toBe(champ)
    }
  })

  it('produit bien un instant zoné, pas la chaîne du champ', () => {
    const iso = champVersIso('2026-10-03T20:00')
    expect(iso).toMatch(/Z$/)
    expect(new Date(iso as string).getHours()).toBe(20)
  })

  it('une valeur vide ou illisible ne casse rien', () => {
    expect(champVersIso('')).toBeNull()
    expect(champVersIso('demain')).toBeNull()
    expect(isoVersChamp(null)).toBe('')
    expect(isoVersChamp('pas-une-date')).toBe('')
  })
})
