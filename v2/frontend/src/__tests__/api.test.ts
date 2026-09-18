import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiDashboard, ErreurApi } from '../api'

/**
 * Le client HTTP lui-même.
 *
 * <p>Rien ne l'éprouvait : chaque test d'écran remplace `apiDashboard` par des
 * doubles, si bien que la couche qui parle réellement au serveur n'était
 * traversée par aucun test. C'est là que vivait une panne visible au PREMIER
 * usage d'un écran : le thème d'un mandat qui n'en a pas encore.
 *
 * <p>Le contrôleur rendait `null`, que Spring traduit par un 200 à corps VIDE.
 * `response.json()` sur une chaîne vide lève une SyntaxError — qui n'est pas une
 * ErreurApi, donc l'écran affichait « Chargement impossible » au lieu de
 * « Aucun thème ». Le double du test, lui, résolvait `null` : un cas que le
 * vrai client ne pouvait pas produire.
 */
describe('client HTTP', () => {
  const reponse = (statut: number, corps: string, type = 'application/json') =>
    ({
      ok: statut >= 200 && statut < 300,
      status: statut,
      text: () => Promise.resolve(corps),
      json: () => (corps ? Promise.resolve(JSON.parse(corps)) : Promise.reject(new SyntaxError())),
      headers: new Headers(type ? { 'Content-Type': type } : {}),
    }) as unknown as Response

  beforeEach(() => vi.restoreAllMocks())
  afterEach(() => vi.restoreAllMocks())

  it('un 200 à corps vide vaut « rien », pas une erreur de syntaxe', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reponse(200, '')))

    await expect(apiDashboard.theme('jeton', 'm1')).resolves.toBeNull()
  })

  it('un 204 vaut « rien » lui aussi', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reponse(204, '')))

    await expect(apiDashboard.theme('jeton', 'm1')).resolves.toBeFalsy()
  })

  it('un corps JSON est rendu tel quel', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      reponse(200, JSON.stringify({ id: 't1', numero: 2, statut: 'BROUILLON' }))))

    await expect(apiDashboard.theme('jeton', 'm1'))
      .resolves.toMatchObject({ id: 't1', numero: 2 })
  })

  it('une erreur du serveur devient une ErreurApi, avec son détail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reponse(409,
      JSON.stringify({ title: 'Opération impossible', detail: 'cette version n’est pas un brouillon' }))))

    await expect(apiDashboard.theme('jeton', 'm1'))
      .rejects.toThrowError(/pas un brouillon/)
  })

  it('les erreurs de schéma d’un bloc sont remontées, pas avalées', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reponse(422, JSON.stringify({
      detail: 'payload invalide', erreurs: ['titre: obligatoire', 'href: format'],
    }))))

    await expect(apiDashboard.theme('jeton', 'm1'))
      .rejects.toThrowError(/titre: obligatoire ; href: format/)
  })

  it('une erreur sans corps JSON reste une ErreurApi lisible', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reponse(502, '<html>bad gateway</html>', 'text/html')))

    const echec = await apiDashboard.theme('jeton', 'm1').catch((e: unknown) => e)
    expect(echec).toBeInstanceOf(ErreurApi)
    expect((echec as ErreurApi).message).toContain('502')
  })

  it('sans jeton, on ne part même pas sur le réseau', async () => {
    const appel = vi.fn()
    vi.stubGlobal('fetch', appel)

    await expect(apiDashboard.theme(null, 'm1')).rejects.toThrowError(/Session expirée/)
    expect(appel).not.toHaveBeenCalled()
  })
})
