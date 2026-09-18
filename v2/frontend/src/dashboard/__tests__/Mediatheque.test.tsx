import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MediaVue } from '../../api'

const apiDashboard = {
  mesPostes: vi.fn(),
  medias: vi.fn(),
  urlsMedias: vi.fn(),
  preparerDepot: vi.fn(),
  confirmerDepot: vi.fn(),
  decrireMedia: vi.fn(),
  supprimerMedia: vi.fn(),
}
const deposerFichier = vi.fn()

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard, deposerFichier }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ jeton: () => 'jeton-de-test' }),
}))

const { default: Mediatheque } = await import('../Mediatheque')

const media = (p: Partial<MediaVue> = {}): MediaVue => ({
  id: 'md1', cle: 'bde/2025-2026/affiche.jpg', contentType: 'image/jpeg',
  tailleOctets: 204_800, largeur: 800, hauteur: 600, blurhash: null,
  texteAlternatif: null, statut: 'DISPONIBLE',
  ...p,
})

function monter() {
  return render(
    <MemoryRouter initialEntries={['/tableau/mandats/m1/medias']}>
      <Routes>
        <Route path="/tableau/mandats/:mandatId/medias" element={<Mediatheque />} />
      </Routes>
    </MemoryRouter>,
  )
}

/**
 * La médiathèque : le premier maillon qui manquait.
 *
 * <p>Le module `media` du serveur était complet et aucun écran ne l'appelait.
 * Ses six routes n'étaient atteignables qu'avec curl, ce qui rendait
 * `evenement.mediaKey` et `partenaire.logoMediaKey` — rendus par le portail
 * PUBLIC — impossibles à renseigner autrement qu'à la main en base.
 */
describe('Médiathèque', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.mesPostes.mockResolvedValue([
      { associationId: 'a1', mandatId: 'm1', anneeCode: '2025-2026',
        poste: 'PRESIDENT', statutMandat: 'EN_FONCTION' },
    ])
    apiDashboard.medias.mockResolvedValue([])
    apiDashboard.urlsMedias.mockResolvedValue({})
  })
  afterEach(cleanup)

  it('lit la médiathèque de l’association et de l’année du mandat', async () => {
    apiDashboard.medias.mockResolvedValue([media()])
    apiDashboard.urlsMedias.mockResolvedValue({
      'bde/2025-2026/affiche.jpg': 'https://minio.exemple.org/signee',
    })
    monter()

    // L'écran ne connaît que le mandat ; la médiathèque est rangée par
    // association ET par année. Les deux viennent des postes de l'utilisateur.
    await waitFor(() =>
      expect(apiDashboard.medias).toHaveBeenCalledWith(expect.anything(), 'a1', '2025-2026'))
    expect(await screen.findByText('bde/2025-2026/affiche.jpg')).toBeTruthy()
  })

  it('le dépôt se fait en trois temps : préparer, envoyer au stockage, confirmer', async () => {
    apiDashboard.preparerDepot.mockResolvedValue({
      mediaId: 'md9', cle: 'bde/2025-2026/x.png',
      url: 'https://minio.exemple.org/x', methode: 'PUT',
      enTetes: { 'Content-Type': 'image/png' },
      expireLe: '2026-10-03T20:10:00Z', tailleMaxOctets: 15 * 1024 * 1024,
    })
    deposerFichier.mockResolvedValue(undefined)
    apiDashboard.confirmerDepot.mockResolvedValue(media({ id: 'md9' }))
    monter()

    await screen.findByLabelText(/déposer un fichier/i)
    await userEvent.upload(
      screen.getByLabelText(/déposer un fichier/i) as HTMLInputElement,
      new File(['octets'], 'x.png', { type: 'image/png' }),
    )

    await waitFor(() => expect(apiDashboard.confirmerDepot).toHaveBeenCalledWith(
      expect.anything(), 'md9'))
    // Confirmer n'est pas une formalité : c'est là que le serveur relit la
    // taille et le TYPE RÉELS dans le stockage, au lieu de croire ce que le
    // navigateur a annoncé. Un SVG déposé sous image/png meurt ici.
    expect(deposerFichier).toHaveBeenCalled()
    expect(apiDashboard.preparerDepot).toHaveBeenCalledWith(
      expect.anything(), 'a1', 'x.png', 'image/png')
  })

  it('le champ n’offre que les types que le serveur accepte', async () => {
    const { TYPES_MEDIA_ACCEPTES } = await import('../../api')
    monter()

    // Première ligne de défense, et la raison pour laquelle le cas « SVG
    // déposé » ne peut pas se jouer ici : le navigateur ne propose même pas le
    // fichier.
    //
    // Ce cas ne prouve QUE le branchement du champ sur la constante partagée —
    // comparer la constante à elle-même ne dirait rien. Que cette constante
    // corresponde bien à ServiceMedia.TYPES_AUTORISES est vérifié là où les
    // deux langages se croisent : ContratApiTest.typesDeMediaAccordes.
    const champ = await screen.findByLabelText(/déposer un fichier/i)
    const accepte = (champ.getAttribute('accept') ?? '').split(',')
    expect(accepte).toEqual([...TYPES_MEDIA_ACCEPTES])
    expect(accepte).not.toContain('image/svg+xml')
  })

  it('un refus du serveur à la CONFIRMATION est montré, pas avalé', async () => {
    const { ErreurApi } = await import('../../api')
    apiDashboard.preparerDepot.mockResolvedValue({
      mediaId: 'md9', cle: 'bde/2025-2026/x.png',
      url: 'https://minio.exemple.org/x', methode: 'PUT',
      enTetes: { 'Content-Type': 'image/png' },
      expireLe: '2026-10-03T20:10:00Z', tailleMaxOctets: 15 * 1024 * 1024,
    })
    deposerFichier.mockResolvedValue(undefined)
    // C'est à la confirmation, et seulement là, que le serveur peut voir que
    // les octets ne sont pas ceux du type annoncé : il les relit dans le
    // stockage. Un document HTML déposé sous image/png meurt ici.
    apiDashboard.confirmerDepot.mockRejectedValue(
      new ErreurApi(422, 'signature du fichier non conforme au type annoncé'))
    monter()

    await screen.findByLabelText(/déposer un fichier/i)
    await userEvent.upload(
      screen.getByLabelText(/déposer un fichier/i) as HTMLInputElement,
      new File(['<html>'], 'x.png', { type: 'image/png' }),
    )

    expect((await screen.findByRole('alert')).textContent)
      .toContain('signature du fichier')
  })

  it('le texte alternatif s’enregistre : c’est lui qui manquait aux images publiques', async () => {
    apiDashboard.medias.mockResolvedValue([media()])
    apiDashboard.decrireMedia.mockResolvedValue(media({ texteAlternatif: 'Affiche du gala' }))
    monter()

    const champ = await screen.findByLabelText(/texte alternatif/i)
    await userEvent.type(champ, 'Affiche du gala')
    await userEvent.click(screen.getByRole('button', { name: /enregistrer/i }))

    await waitFor(() => expect(apiDashboard.decrireMedia).toHaveBeenCalledWith(
      expect.anything(), 'md1', 'Affiche du gala'))
  })

  it('un média non disponible n’a ni image ni texte alternatif à saisir', async () => {
    apiDashboard.medias.mockResolvedValue([media({ statut: 'REJETE' })])
    monter()

    await screen.findByText(/refusé à la vérification/i)
    // Proposer de décrire un fichier que le serveur a refusé serait proposer
    // d'habiller un objet qui n'existe pas dans le stockage.
    expect(screen.queryByLabelText(/texte alternatif/i)).toBeNull()
    expect(apiDashboard.urlsMedias).toHaveBeenCalledWith(expect.anything(), [])
  })

  it('retirer un média le dit, et recharge la liste', async () => {
    apiDashboard.medias.mockResolvedValue([media()])
    apiDashboard.supprimerMedia.mockResolvedValue(undefined)
    monter()

    await userEvent.click(await screen.findByRole('button', { name: /retirer/i }))

    await waitFor(() => expect(apiDashboard.supprimerMedia).toHaveBeenCalledWith(
      expect.anything(), 'md1'))
    expect(apiDashboard.medias).toHaveBeenCalledTimes(2)
  })
})
