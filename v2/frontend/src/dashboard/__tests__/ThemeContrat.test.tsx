import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { variablesDuTheme } from '../../theme'
import type { Theme } from '../../types'
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

const auth = { jeton: () => 'jeton-de-test' }
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }))

const { default: Theme_ } = await import('../Theme')

const arbre = () => (
  <MemoryRouter initialEntries={['/tableau/mandats/m1/theme']}>
    <Routes>
      <Route path="/tableau/mandats/:mandatId/theme" element={<Theme_ />} />
    </Routes>
  </MemoryRouter>
)

/**
 * Le contrat entre l'écran qui ÉCRIT un thème et le portail qui le LIT.
 *
 * <p>Il n'existait pas, et les deux côtés parlaient deux langues : l'écran
 * enregistrait `couleurPrimaire`, `couleurSecondaire`, `couleurFond`,
 * `couleurTexte` ; `variablesDuTheme` lit `accent`, `accentContraste`, `encre`,
 * `fond`, `rayon`, `police`. Aucune des quatre clés écrites n'était donc jamais
 * lue : un bureau choisissait ses couleurs, enregistrait, publiait — et sa page
 * publique retombait sur les valeurs par défaut. Et comme le payload REMPLACE
 * le précédent, un thème posé autrement (clonage à la passation) était détruit
 * par le premier enregistrement.
 *
 * <p>Chacun des deux côtés était vert de son côté. C'est la jointure que
 * personne ne vérifiait — alors qu'elle est toute la promesse de la
 * fonctionnalité.
 */
describe('contrat entre l’écran de thème et le rendu public', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.theme.mockResolvedValue(null)
    apiDashboard.enregistrerTheme.mockResolvedValue(null)
  })
  afterEach(cleanup)

  /** Ce que l'écran envoie réellement au serveur quand on clique « Enregistrer ». */
  async function cequOnEnregistre(): Promise<Record<string, string>> {
    render(arbre())
    await waitFor(() => expect(screen.getByText(/Aucun thème/)).toBeTruthy())
    screen.getByRole('button', { name: /Enregistrer/ }).click()
    await waitFor(() => expect(apiDashboard.enregistrerTheme).toHaveBeenCalledTimes(1))
    return apiDashboard.enregistrerTheme.mock.calls[0]?.[2] as Record<string, string>
  }

  it('chaque clé enregistrée est une clé que le portail LIT', async () => {
    const envoye = await cequOnEnregistre()
    const lues = Object.keys(variablesDuTheme({}))
      .map((v) => v.replace(/^--/, ''))

    // accent-contraste ↔ accentContraste : la variable CSS est en kebab-case,
    // le jeton en camelCase. On compare donc sur une forme normalisée.
    const normal = (s: string) => s.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())
    const connues = new Set(lues.map(normal))

    expect(Object.keys(envoye)).not.toHaveLength(0)
    for (const cle of Object.keys(envoye)) {
      expect(connues, `« ${cle} » n’est lu par personne`).toContain(cle)
    }
  })

  it('les valeurs choisies arrivent VRAIMENT dans les variables CSS', async () => {
    const envoye = await cequOnEnregistre()

    // Le bout-à-bout : on prend ce que l'écran envoie, on le passe au
    // traducteur du portail, et on vérifie que les choix ressortent. C'est
    // cette jointure-là qui était cassée.
    const variables = variablesDuTheme(envoye as Theme) as Record<string, string>
    expect(variables['--accent']).toBe(envoye.accent)
    expect(variables['--fond']).toBe(envoye.fond)
    expect(variables['--encre']).toBe(envoye.encre)
    expect(variables['--accent-contraste']).toBe(envoye.accentContraste)
    expect(variables['--rayon']).toBe(envoye.rayon)
  })

  it('un thème déjà enregistré est relu par l’écran, pas écrasé', async () => {
    apiDashboard.theme.mockResolvedValue({
      id: 't1', mandatId: 'm1', numero: 2, statut: 'BROUILLON',
      tokens: JSON.stringify({ accent: '#123456', fond: '#fafafa' }),
    } satisfies ThemeVue)

    render(arbre())
    await waitFor(() =>
      expect((screen.getByLabelText('Couleur principale') as HTMLInputElement).value)
        .toBe('#123456'))
    expect((screen.getByLabelText('Fond') as HTMLInputElement).value).toBe('#fafafa')
  })

  it('une valeur de jeton hostile n’atteint jamais la page', () => {
    // Ces valeurs viennent de la base, donc d'un bureau. Sans contrôle, elles
    // atterrissaient telles quelles dans une variable CSS consommée en
    // `background` : chaque visiteur émettait une requête vers un tiers.
    const variables = variablesDuTheme({
      accent: 'url(https://tiers.example/pixel.png)',
      fond: 'red; background-image: url(//tiers.example/p.png)',
      encre: 'javascript:alert(1)',
      rayon: '9999px; position: fixed',
    } as Theme) as Record<string, string>

    for (const [nom, valeur] of Object.entries(variables)) {
      expect(valeur, `${nom} laisse passer une valeur libre`).not.toContain('url(')
      expect(valeur).not.toContain('tiers.example')
      expect(valeur).not.toContain(';')
    }
    expect(variables['--accent']).toBe('#1F4E79')
  })

  it('une valeur légitime n’est pas rejetée par excès de zèle', () => {
    const variables = variablesDuTheme({
      accent: '#8B1E3F', accentContraste: '#fff', encre: '#101820',
      fond: '#F6F7F9', rayon: '16px', police: 'SERIF',
    }) as Record<string, string>

    expect(variables['--accent']).toBe('#8B1E3F')
    expect(variables['--accent-contraste']).toBe('#fff')
    expect(variables['--rayon']).toBe('16px')
    expect(variables['--police']).toContain('serif')
  })
})
