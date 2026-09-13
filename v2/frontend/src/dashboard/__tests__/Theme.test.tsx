import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ThemeVue } from '../../api'

const apiDashboard = {
  theme: vi.fn(),
  enregistrerTheme: vi.fn(),
  publierTheme: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})

// Objet STABLE : le vrai fournisseur mémorise `jeton` avec useCallback. Un
// mock qui rend un objet neuf à chaque rendu ferait repartir l'effet de
// chargement sans fin — le test tournerait en boucle au lieu d'échouer.
const auth = { jeton: () => 'jeton-de-test' }
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }))

const { default: Theme } = await import('../Theme')

const vue = (p: Partial<ThemeVue> = {}): ThemeVue => ({
  id: 't1', mandatId: 'm1', numero: 1, statut: 'BROUILLON',
  tokens: JSON.stringify({ couleurPrimaire: '#8B1E3F' }),
  ...p,
})

const monter = () =>
  render(
    <MemoryRouter initialEntries={['/tableau/mandats/m1/theme']}>
      <Routes>
        <Route path="/tableau/mandats/:mandatId/theme" element={<Theme />} />
      </Routes>
    </MemoryRouter>,
  )

/**
 * L'écran de thème.
 *
 * <p>Il manquait entièrement, et avec lui toute possibilité pour un bureau de
 * choisir ses couleurs : la table, l'index « une seule version publiée », les
 * transitions du domaine, le clonage à la passation, le rendu par le portail et
 * la permission THEME_EDITER existaient tous, sans aucune route ni aucun écran.
 */
describe('thème du mandat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.theme.mockResolvedValue(null)
  })
  afterEach(cleanup)

  it("sans thème, l'écran le dit et propose des valeurs par défaut", async () => {
    monter()
    await waitFor(() => expect(screen.getByText(/Aucun thème/)).toBeTruthy())
    expect((screen.getByLabelText('Couleur principale') as HTMLInputElement).value)
      .toBe('#8b1e3f')
  })

  it('enregistrer envoie les jetons, et ne publie PAS', async () => {
    apiDashboard.enregistrerTheme.mockResolvedValue(vue())
    monter()
    await waitFor(() => expect(screen.getByText(/Aucun thème/)).toBeTruthy())

    screen.getByRole('button', { name: /Enregistrer/ }).click()

    await waitFor(() => expect(apiDashboard.enregistrerTheme).toHaveBeenCalledTimes(1))
    expect(apiDashboard.enregistrerTheme.mock.calls[0][2])
      .toHaveProperty('couleurPrimaire')
    // Enregistrer ne doit jamais changer ce que voient les visiteurs.
    expect(apiDashboard.publierTheme).not.toHaveBeenCalled()
  })

  it('publier est une action distincte du bouton d’enregistrement', async () => {
    apiDashboard.theme.mockResolvedValue(vue({ statut: 'BROUILLON' }))
    apiDashboard.publierTheme.mockResolvedValue(vue({ statut: 'PUBLIEE' }))
    monter()
    await waitFor(() => expect(screen.getByText(/brouillon non publié/)).toBeTruthy())

    screen.getByRole('button', { name: /^Publier$/ }).click()

    await waitFor(() => expect(apiDashboard.publierTheme).toHaveBeenCalledTimes(1))
    expect(apiDashboard.enregistrerTheme).not.toHaveBeenCalled()
  })

  it('un thème déjà publié ne se republie pas', async () => {
    apiDashboard.theme.mockResolvedValue(vue({ numero: 3, statut: 'PUBLIEE' }))
    monter()
    await waitFor(() => expect(screen.getByText(/Version 3 — publiée/)).toBeTruthy())

    expect((screen.getByRole('button', { name: /^Publier$/ }) as HTMLButtonElement).disabled)
      .toBe(true)
  })

  it('un thème existant est relu, pas écrasé par les valeurs par défaut', async () => {
    apiDashboard.theme.mockResolvedValue(
      vue({ tokens: JSON.stringify({ couleurPrimaire: '#123456' }) }))
    monter()
    await waitFor(() =>
      expect((screen.getByLabelText('Couleur principale') as HTMLInputElement).value)
        .toBe('#123456'))
  })

  it('un thème aux jetons illisibles n’empêche pas d’en composer un nouveau', async () => {
    apiDashboard.theme.mockResolvedValue(vue({ tokens: '{ceci n’est pas du JSON' }))
    monter()
    await waitFor(() =>
      expect((screen.getByLabelText('Couleur principale') as HTMLInputElement).value)
        .toBe('#8b1e3f'))
  })
})
