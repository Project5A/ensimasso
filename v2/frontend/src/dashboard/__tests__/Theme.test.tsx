import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

// Le vrai fournisseur remplace cet objet à CHAQUE renouvellement silencieux du
// jeton OIDC : `jeton` est un useCallback sur `utilisateur`, et `addUserLoaded`
// remplace l'utilisateur toutes les quelques minutes. `renouvelerLeJeton()`
// reproduit exactement ça. (Le rendre neuf à CHAQUE rendu, en revanche, ferait
// tourner la suite en boucle au lieu d'échouer.)
let auth = { jeton: () => 'jeton-de-test' }
const renouvelerLeJeton = () => { auth = { jeton: () => 'jeton-renouvele' } }
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }))

const { default: Theme } = await import('../Theme')

const vue = (p: Partial<ThemeVue> = {}): ThemeVue => ({
  id: 't1', mandatId: 'm1', numero: 1, statut: 'BROUILLON',
  tokens: JSON.stringify({ couleurPrimaire: '#8B1E3F' }),
  ...p,
})

// Une FONCTION, pas une constante : React abandonne le rendu quand on lui
// repasse l'élément identique, et un `rerender` sur la même référence ne
// rejouerait rien — le cas du renouvellement passerait au vert sans avoir rien
// éprouvé.
const arbre = () => (
  <MemoryRouter initialEntries={['/tableau/mandats/m1/theme']}>
    <Routes>
      <Route path="/tableau/mandats/:mandatId/theme" element={<Theme />} />
    </Routes>
  </MemoryRouter>
)

const monter = () => render(arbre())

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
    auth = { jeton: () => 'jeton-de-test' }
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
    expect(apiDashboard.enregistrerTheme.mock.calls[0]?.[2])
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

  it('le renouvellement du jeton n’efface pas les couleurs en cours de choix', async () => {
    apiDashboard.theme.mockResolvedValue(vue({
      tokens: JSON.stringify({ couleurPrimaire: '#123456' }),
    }))
    const { rerender } = monter()
    const champ = () => screen.getByLabelText('Couleur principale') as HTMLInputElement
    await waitFor(() => expect(champ().value).toBe('#123456'))

    fireEvent.change(champ(), { target: { value: '#00ff00' } })
    expect(champ().value).toBe('#00ff00')

    // Toutes les quelques minutes, en vrai, sans que personne ne touche à rien.
    renouvelerLeJeton()
    rerender(arbre())

    // Le rechargement rejouait `setValeurs(lire(t))` et rendait au bureau la
    // couleur enregistrée, en effaçant celle qu'il venait de choisir.
    await waitFor(() => expect(apiDashboard.theme).toHaveBeenCalledTimes(1))
    expect(champ().value).toBe('#00ff00')
  })

  it('le jeton utilisé reste celui du moment, pas celui du montage', async () => {
    apiDashboard.theme.mockResolvedValue(vue({ statut: 'BROUILLON' }))
    apiDashboard.publierTheme.mockResolvedValue(vue({ statut: 'PUBLIEE' }))
    const { rerender } = monter()
    await waitFor(() => expect(screen.getByText(/brouillon non publié/)).toBeTruthy())

    renouvelerLeJeton()
    rerender(arbre())

    screen.getByRole('button', { name: /^Publier$/ }).click()

    // La référence sert à ne pas RELANCER le chargement, pas à figer le jeton :
    // un appel avec un jeton périmé partirait en 401.
    await waitFor(() => expect(apiDashboard.publierTheme).toHaveBeenCalledTimes(1))
    expect(apiDashboard.publierTheme.mock.calls[0]?.[0]).toBe('jeton-renouvele')
  })

  it('un thème aux jetons illisibles n’empêche pas d’en composer un nouveau', async () => {
    apiDashboard.theme.mockResolvedValue(vue({ tokens: '{ceci n’est pas du JSON' }))
    monter()
    await waitFor(() =>
      expect((screen.getByLabelText('Couleur principale') as HTMLInputElement).value)
        .toBe('#8b1e3f'))
  })
})
