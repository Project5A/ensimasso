import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PosteVue } from '../../api'

const apiDashboard = { mesPostes: vi.fn() }

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    jeton: () => 'jeton-de-test',
    deconnecter: vi.fn(),
    utilisateur: { profile: { preferred_username: 'yahya' } },
  }),
}))

const { default: Tableau } = await import('../Tableau')

const poste = (p: Partial<PosteVue> = {}): PosteVue => ({
  associationId: 'a1', mandatId: 'm1', anneeCode: '2025-2026',
  poste: 'PRESIDENT', statutMandat: 'EN_FONCTION',
  ...p,
})

function monter() {
  return render(<MemoryRouter><Tableau /></MemoryRouter>)
}

/**
 * Cet écran est le SEUL chemin vers un mandat : il n'existe aucune autre
 * navigation, et l'URL d'un mandat contient un UUID. Ce qu'il n'affiche pas
 * est donc inatteignable.
 */
describe('Tableau de bord', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('mène au mandat du bureau ENTRANT, et le dit', async () => {
    apiDashboard.mesPostes.mockResolvedValue([
      poste({ mandatId: 'm-entrant', anneeCode: '2026-2027', statutMandat: 'PREPARATION' }),
    ])
    monter()

    // Le lien manquait entièrement : la requête qui alimente cet écran ne
    // rendait que les mandats EN_FONCTION. Un bureau tout juste désigné lisait
    // « Aucun mandat » — et, en dessous, qu'il devait se faire désigner.
    const lien = await screen.findByRole('link', { name: /2026-2027/ })
    expect(lien.getAttribute('href')).toBe('/tableau/mandats/m-entrant')
    expect(screen.getByText(/bureau entrant/i)).toBeTruthy()
  })

  it('distingue le mandat en fonction du mandat en préparation', async () => {
    apiDashboard.mesPostes.mockResolvedValue([
      poste({ mandatId: 'm-actuel', anneeCode: '2025-2026', statutMandat: 'EN_FONCTION' }),
      poste({ mandatId: 'm-entrant', anneeCode: '2026-2027', statutMandat: 'PREPARATION' }),
    ])
    monter()

    await screen.findByText('2025-2026')
    // Deux entrées identiques, c'est publier au mauvais endroit : la mention
    // n'apparaît que sur celle qui n'est pas encore en ligne.
    expect(screen.getAllByText(/bureau entrant/i)).toHaveLength(1)
    expect(screen.getByRole('link', { name: /2026-2027/ }).textContent)
      .toMatch(/bureau entrant/i)
    expect(screen.getByRole('link', { name: /2025-2026/ }).textContent)
      .not.toMatch(/bureau entrant/i)
  })

  it('sans aucun mandat, ne prétend pas que seuls les bureaux en fonction comptent', async () => {
    apiDashboard.mesPostes.mockResolvedValue([])
    monter()

    await screen.findByRole('heading', { name: /aucun mandat/i })
    expect(screen.getByText(/ni en fonction ni en préparation/i)).toBeTruthy()
  })
})
