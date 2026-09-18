import { describe, expect, it } from 'vitest'
import { champVersIso, isoVersChamp } from '../dates'

describe('champs de date', () => {
  // Sans cette garde, tout le reste du fichier est muet.
  //
  // Ces tests tournaient en UTC. Or en UTC, l'implémentation naïve que ce
  // fichier existe pour interdire — coller un « Z » sur la valeur du champ —
  // donne exactement le même résultat que la conversion correcte. Les
  // allers-retours passaient donc au vert sur le défaut lui-même, et le cas
  // nommé « tient aussi de l'autre côté du changement d'heure » traversait un
  // fuseau qui n'en a pas.
  it('tourne dans un fuseau décalé ET à changement d’heure, sinon il ne prouve rien', () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe('Europe/Paris')

    const hiver = new Date('2026-01-15T12:00:00Z').getHours()
    const ete = new Date('2026-07-15T12:00:00Z').getHours()
    expect(hiver).toBe(13)     // UTC+1
    expect(ete).toBe(14)       // UTC+2 : le changement d’heure existe bien
  })
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

  it('applique le décalage réel, et le bon selon la saison', () => {
    // L'assertion qui manquait : l'aller-retour seul est satisfait par toute
    // conversion symétrique, y compris celle qui ne convertit rien. Ici on
    // nomme l'instant attendu, donc le décalage.
    expect(champVersIso('2026-10-03T20:00')).toBe('2026-10-03T18:00:00.000Z')   // CEST, UTC+2
    expect(champVersIso('2026-01-15T09:30')).toBe('2026-01-15T08:30:00.000Z')   // CET,  UTC+1

    // Et dans l'autre sens, y compris quand le décalage fait changer de jour.
    expect(isoVersChamp('2026-01-01T23:30:00.000Z')).toBe('2026-01-02T00:30')
  })

  it('une valeur vide ou illisible ne casse rien', () => {
    expect(champVersIso('')).toBeNull()
    expect(champVersIso('demain')).toBeNull()
    expect(isoVersChamp(null)).toBe('')
    expect(isoVersChamp('pas-une-date')).toBe('')
  })
})
