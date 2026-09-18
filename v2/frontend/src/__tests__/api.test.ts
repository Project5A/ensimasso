import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiDashboard, deposerFichier, ErreurApi, type DepotVue } from '../api'

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

  // ------------------------------------------- dépôt direct vers le stockage

  const depot = (p: Partial<DepotVue> = {}): DepotVue => ({
    mediaId: 'md1', cle: 'bde/2025-2026/a.jpg',
    url: 'https://minio.exemple.org/media/bde/2025-2026/a.jpg?X-Amz-Signature=abc',
    methode: 'PUT', enTetes: { 'Content-Type': 'image/jpeg' },
    expireLe: '2026-10-03T20:10:00Z', tailleMaxOctets: 15 * 1024 * 1024,
    ...p,
  })

  it('le dépôt va au STOCKAGE, et n’emporte JAMAIS le jeton Keycloak', async () => {
    const appels = vi.fn().mockResolvedValue({ ok: true, status: 200 } as Response)
    vi.stubGlobal('fetch', appels)

    await deposerFichier(depot(), new File(['x'], 'a.jpg', { type: 'image/jpeg' }))

    expect(appels).toHaveBeenCalledTimes(1)
    const [url, init] = appels.mock.calls[0] as [string, RequestInit]

    // L'octet part chez MinIO/S3, pas chez nous : le fichier n'a rien à faire
    // à travers un pod Java, et 15 Mo par affiche le montreraient vite.
    expect(url).toContain('minio.exemple.org')
    expect(url).not.toContain('/api/')
    expect(init.method).toBe('PUT')

    // LE point : envoyer l'Authorization à un hôte tiers divulguerait le jeton
    // Keycloak de l’utilisateur à ce tiers. Seuls les en-têtes que le serveur
    // a SIGNÉS partent.
    const entetes = (init.headers ?? {}) as Record<string, string>
    expect(Object.keys(entetes).map((k) => k.toLowerCase())).not.toContain('authorization')
    expect(entetes['Content-Type']).toBe('image/jpeg')
  })

  it('un fichier trop volumineux est refusé AVANT d’être envoyé', async () => {
    const appels = vi.fn()
    vi.stubGlobal('fetch', appels)

    const gros = new File(['x'], 'gros.jpg', { type: 'image/jpeg' })
    Object.defineProperty(gros, 'size', { value: 20 * 1024 * 1024 })

    await expect(deposerFichier(depot(), gros)).rejects.toBeInstanceOf(ErreurApi)
    // Téléverser vingt méga-octets pour se faire répondre non au bout, c’est
    // la connexion de quelqu’un qu’on dépense.
    expect(appels).not.toHaveBeenCalled()
  })

  it('un refus du stockage devient une ErreurApi, pas un silence', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403 } as Response))

    await expect(deposerFichier(depot(), new File(['x'], 'a.jpg', { type: 'image/jpeg' })))
      .rejects.toBeInstanceOf(ErreurApi)
  })

  it('résoudre zéro clé ne fait aucune requête', async () => {
    const appels = vi.fn()
    vi.stubGlobal('fetch', appels)

    await expect(apiDashboard.urlsMedias('jeton', [])).resolves.toEqual({})
    // Une galerie vide ne doit pas réveiller le serveur.
    expect(appels).not.toHaveBeenCalled()
  })
})
