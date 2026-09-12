import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PartenaireDashboard } from '../../api'

const apiDashboard = {
  partenaires: vi.fn(),
  creerPartenaire: vi.fn(),
  modifierPartenaire: vi.fn(),
  supprimerPartenaire: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ jeton: () => 'jeton-de-test' }),
}))

const { default: Partenaires } = await import('../Partenaires')

const pa = (p: Partial<PartenaireDashboard> = {}): PartenaireDashboard => ({
  id: 'p1', nom: 'Le Mans Métropole', niveau: 'OR',
  logoMediaKey: null, url: null, ordre: 0, visible: true,
  ...p,
})

function monter() {
  return render(
    <MemoryRouter initialEntries={['/tableau/mandats/m1/partenaires']}>
      <Routes>
        <Route path="/tableau/mandats/:mandatId/partenaires" element={<Partenaires />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Partenaires du tableau de bord', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('affiche le niveau, et signale ce qui ne sort pas sur le site', async () => {
    apiDashboard.partenaires.mockResolvedValue([
      pa(),
      pa({ id: 'p2', nom: 'En discussion', niveau: 'BRONZE', visible: false }),
    ])
    monter()
    await screen.findByText('Le Mans Métropole')

    expect(screen.getByText('OR')).toBeDefined()
    expect(screen.getByText('Masqué')).toBeDefined()
  })

  it("la liste vide explique qu'elle appartient à CE mandat", async () => {
    apiDashboard.partenaires.mockResolvedValue([])
    monter()
    // La question que pose tout nouveau bureau : « pourquoi est-ce vide, on
    // avait des partenaires ». La réponse est dans l'écran.
    expect(await screen.findByText(/propre à ce mandat/)).toBeDefined()
  })

  it('la création envoie le niveau choisi et la visibilité', async () => {
    apiDashboard.partenaires.mockResolvedValue([])
    apiDashboard.creerPartenaire.mockResolvedValue(pa())
    monter()
    await screen.findByRole('heading', { name: 'Nouveau partenaire' })

    const u = userEvent.setup()
    await u.type(screen.getByLabelText('Nom'), 'MMArena')
    await u.selectOptions(screen.getByLabelText('Niveau'), 'ARGENT')
    await u.click(screen.getByLabelText(/Afficher sur les pages publiques/))
    await u.click(screen.getByRole('button', { name: 'Ajouter' }))

    await waitFor(() => expect(apiDashboard.creerPartenaire).toHaveBeenCalled())
    const appel = apiDashboard.creerPartenaire.mock.calls[0]
    const [, mandatId, corps] = appel ?? []
    expect(mandatId).toBe('m1')
    expect(corps).toMatchObject({ nom: 'MMArena', niveau: 'ARGENT', visible: false })
  })

  it('modifier pré-remplit le formulaire avec le partenaire choisi', async () => {
    apiDashboard.partenaires.mockResolvedValue([pa({ url: 'https://exemple.fr' })])
    monter()
    await screen.findByText('Le Mans Métropole')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Modifier' }))
    expect(screen.getByLabelText('Nom')).toHaveProperty('value', 'Le Mans Métropole')
    expect(screen.getByLabelText('Niveau')).toHaveProperty('value', 'OR')
    expect(screen.getByLabelText(/Site/)).toHaveProperty('value', 'https://exemple.fr')
  })
})
