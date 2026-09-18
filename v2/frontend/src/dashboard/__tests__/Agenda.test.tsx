import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { EvenementDashboard } from '../../api'

const apiDashboard = {
  evenements: vi.fn(),
  creerEvenement: vi.fn(),
  modifierEvenement: vi.fn(),
  publierEvenement: vi.fn(),
  annulerEvenement: vi.fn(),
  supprimerEvenement: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ jeton: () => 'jeton-de-test' }),
}))

const { default: Agenda } = await import('../Agenda')

const ev = (p: Partial<EvenementDashboard> = {}): EvenementDashboard => ({
  id: 'e1', slug: 'gala', titre: 'Gala',
  resume: null, description: null, lieu: 'Palais',
  debutLe: '2026-10-03T20:00:00Z', finLe: null,
  mediaKey: null, lien: null,
  statut: 'BROUILLON', complet: false, motifAnnulation: null,
  ...p,
})

function monter() {
  return render(
    <MemoryRouter initialEntries={['/tableau/mandats/m1/agenda']}>
      <Routes>
        <Route path="/tableau/mandats/:mandatId/agenda" element={<Agenda />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Agenda du tableau de bord', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('les actions suivent le cycle de vie : un brouillon se publie et se supprime', async () => {
    apiDashboard.evenements.mockResolvedValue([ev()])
    monter()
    await screen.findByText('Gala')

    expect(screen.getByRole('button', { name: 'Publier' })).toBeDefined()
    expect(screen.getByRole('button', { name: 'Supprimer' })).toBeDefined()
    expect(screen.queryByRole('button', { name: 'Annuler' })).toBeNull()
  })

  it("un évènement publié s'annule et ne se supprime plus", async () => {
    apiDashboard.evenements.mockResolvedValue([ev({ statut: 'PUBLIE' })])
    monter()
    await screen.findByText('Gala')

    // Le serveur refuse la suppression d'un évènement publié ; l'écran ne
    // propose pas un bouton qui échouerait.
    expect(screen.queryByRole('button', { name: 'Supprimer' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Annuler' })).toBeDefined()
  })

  it('un évènement annulé n’offre plus ni publication ni annulation', async () => {
    apiDashboard.evenements.mockResolvedValue([
      ev({ statut: 'ANNULE', motifAnnulation: 'Salle indisponible' }),
    ])
    monter()
    await screen.findByText('Gala')

    expect(screen.getByText(/Salle indisponible/)).toBeDefined()
    expect(screen.queryByRole('button', { name: 'Publier' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Annuler' })).toBeNull()
  })

  it('la création envoie un instant zoné et un slug dérivé du titre', async () => {
    const { userEvent } = await import('@testing-library/user-event')
    apiDashboard.evenements.mockResolvedValue([])
    apiDashboard.creerEvenement.mockResolvedValue(ev())
    monter()
    await screen.findByRole('heading', { name: 'Nouvel évènement' })

    const u = userEvent.setup()
    await u.type(screen.getByLabelText('Titre'), 'Soirée de Noël')
    await u.type(screen.getByLabelText('Début'), '2026-12-20T20:00')
    await u.click(screen.getByRole('button', { name: 'Créer le brouillon' }))

    await waitFor(() => expect(apiDashboard.creerEvenement).toHaveBeenCalled())
    const corps = apiDashboard.creerEvenement.mock.calls[0]?.[2]
    expect(corps).toBeDefined()
    expect(corps.slug).toBe('soiree-de-noel')
    expect(corps.titre).toBe('Soirée de Noël')
    // L'API attend un instant : le champ HTML, lui, n'a pas de fuseau.
    expect(corps.debutLe).toMatch(/Z$/)
    expect(new Date(corps.debutLe).getHours()).toBe(20)
  })

  it("l'annulation se saisit en ligne et envoie le motif", async () => {
    const { userEvent } = await import('@testing-library/user-event')
    apiDashboard.evenements.mockResolvedValue([ev({ statut: 'PUBLIE' })])
    apiDashboard.annulerEvenement.mockResolvedValue(ev({ statut: 'ANNULE' }))
    monter()
    await screen.findByText('Gala')

    const u = userEvent.setup()
    await u.click(screen.getByRole('button', { name: 'Annuler' }))
    // Le motif part sur la page publique : il se relit avant d'être envoyé.
    await u.type(screen.getByLabelText(/Motif d'annulation/), 'Salle indisponible')
    await u.click(screen.getByRole('button', { name: /Confirmer l'annulation/ }))

    await waitFor(() => expect(apiDashboard.annulerEvenement).toHaveBeenCalled())
    expect(apiDashboard.annulerEvenement.mock.calls[0]?.[2]).toBe('Salle indisponible')
  })

  it('on peut renoncer à une annulation sans rien envoyer', async () => {
    const { userEvent } = await import('@testing-library/user-event')
    apiDashboard.evenements.mockResolvedValue([ev({ statut: 'PUBLIE' })])
    monter()
    await screen.findByText('Gala')

    const u = userEvent.setup()
    await u.click(screen.getByRole('button', { name: 'Annuler' }))
    await u.click(screen.getByRole('button', { name: 'Renoncer' }))

    expect(screen.queryByLabelText(/Motif d'annulation/)).toBeNull()
    expect(apiDashboard.annulerEvenement).not.toHaveBeenCalled()
  })

  it('un refus du serveur est montré, pas avalé', async () => {
    const { ErreurApi } = await import('../../api')
    apiDashboard.evenements.mockRejectedValue(new ErreurApi(403, 'permission refusée'))
    monter()
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'permission refusée')
  })

  it("modifier un évènement n'efface pas son affiche ni sa description", async () => {
    apiDashboard.evenements.mockResolvedValue([
      ev({ mediaKey: 'bde/2025-2026/affiche.jpg', description: 'Le mot du président.' }),
    ])
    apiDashboard.modifierEvenement.mockResolvedValue(ev())
    monter()
    const { userEvent } = await import('@testing-library/user-event')
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Modifier' }))
    await u.click(screen.getByRole('button', { name: /enregistrer/i }))

    // Les deux champs n'ont pas de saisie à l'écran et partaient à null. Le
    // service REMPLACE tout — decrire(...) prend les neuf champs — donc ouvrir
    // un évènement et enregistrer effaçait son affiche, que le portail public
    // affiche, et sa description. En silence.
    await waitFor(() =>
      expect(apiDashboard.modifierEvenement).toHaveBeenCalledWith(
        expect.anything(),
        'e1',
        expect.objectContaining({
          mediaKey: 'bde/2025-2026/affiche.jpg',
          description: 'Le mot du président.',
        }),
      ),
    )
  })

  it("supprimer l'évènement en cours d'édition referme le formulaire", async () => {
    apiDashboard.evenements.mockResolvedValue([ev()])
    apiDashboard.supprimerEvenement.mockResolvedValue(undefined)
    monter()
    const { userEvent } = await import('@testing-library/user-event')
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Modifier' }))
    expect(screen.getByRole('button', { name: /abandonner/i })).toBeTruthy()

    apiDashboard.evenements.mockResolvedValue([])
    await u.click(screen.getByRole('button', { name: 'Supprimer' }))

    // Le formulaire restait ouvert sur un identifiant mort : « Enregistrer »
    // revenait en « évènement introuvable », sur une saisie encore à l'écran.
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /abandonner/i })).toBeNull())
    expect((screen.getByLabelText(/titre/i) as HTMLInputElement).value).toBe('')
  })
})
