import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BlocVue, TypeBlocVue, VersionVue } from '../../api'

const apiDashboard = {
  ouvrirBrouillon: vi.fn(),
  catalogue: vi.fn(),
  blocs: vi.fn(),
  ajouterBloc: vi.fn(),
  modifierBloc: vi.fn(),
  supprimerBloc: vi.fn(),
  reordonner: vi.fn(),
  publier: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})

// Objet STABLE : l'effet d'ouverture dépend de `jeton`. Un mock qui rend un
// objet neuf à chaque rendu relancerait l'ouverture sans fin — la suite
// tournerait en boucle au lieu d'échouer.
const auth = { jeton: () => 'jeton-de-test' }
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }))

const { default: Editeur } = await import('../Editeur')

const version = (p: Partial<VersionVue> = {}): VersionVue => ({
  id: 'v4', pageId: 'p1', numero: 4, statut: 'BROUILLON',
  publieLe: null, note: null,
  ...p,
})

const BROUILLON_4 = version()
const PUBLIEE_4 = version({ statut: 'PUBLIEE', publieLe: '2026-03-01T10:00:00Z' })
const BROUILLON_5 = version({ id: 'v5', numero: 5 })

const TEXTE: TypeBlocVue = {
  type: 'TEXTE', schemaVersion: 1, libelle: 'Texte', categorie: 'Contenu',
  composantReact: 'Texte',
  jsonSchema: JSON.stringify({ type: 'object', properties: { corps: { type: 'string' } } }),
  payloadDefaut: JSON.stringify({ corps: 'À écrire' }),
}

/** Les blocs d'une version. Leurs identifiants portent celui de la version :
 *  c'est ce qui permet de voir, depuis un clic, sur quelle version l'éditeur
 *  travaille réellement. */
const blocsDe = (v: string): BlocVue[] => [
  { id: `${v}-a`, ordre: 0, type: 'TEXTE', schemaVersion: 1, payload: '{}', visible: true },
  { id: `${v}-b`, ordre: 1, type: 'TEXTE', schemaVersion: 1, payload: '{}', visible: true },
]

const monter = () =>
  render(
    <MemoryRouter initialEntries={['/tableau/pages/p1']}>
      <Routes>
        <Route path="/tableau/pages/:pageId" element={<Editeur />} />
      </Routes>
    </MemoryRouter>,
  )

const bouton = (nom: RegExp) => screen.getByRole('button', { name: nom }) as HTMLButtonElement
const boutons = (nom: RegExp) =>
  screen.getAllByRole('button', { name: nom }) as HTMLButtonElement[]

// La palette porte le libellé du type ET sa catégorie ; la barre d'une ligne
// porte le seul libellé. Sans quoi les deux se confondent.
const palette = () => bouton(/^Texte Contenu$/)
/** Le bouton réellement actionnable de la paire : monter la 2ᵉ ligne, descendre
 *  la 1ʳᵉ. Les deux autres sont désactivés par leur position, pas par l'état de
 *  la version — les confondre rendrait le cas « publiée » vide de sens. */
const monterLaSeconde = () => boutons(/^Monter$/)[1] as HTMLButtonElement
const descendreLaPremiere = () => boutons(/^Descendre$/)[0] as HTMLButtonElement

/**
 * L'éditeur de page, autour de la publication.
 *
 * <p>Publier FIGE la version : le domaine refuse ensuite toute écriture sur
 * elle, et le trigger `bloc_fige` refuse jusqu'aux écritures sur ses blocs.
 * L'éditeur gardait pourtant la même version dans son état — le bandeau
 * continuait d'annoncer un brouillon, et chaque action suivante partait sur une
 * version publiée. Rien dans l'interface ne disait pourquoi ; on obtenait un
 * 409 par clic.
 */
describe('éditeur de page : ce qui change quand on publie', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.ouvrirBrouillon.mockResolvedValue(BROUILLON_4)
    apiDashboard.catalogue.mockResolvedValue([TEXTE])
    apiDashboard.blocs.mockImplementation(
      async (_jeton: unknown, versionId: string) => blocsDe(versionId))
    apiDashboard.publier.mockResolvedValue(PUBLIEE_4)
  })
  afterEach(cleanup)

  const attendreOuverture = () =>
    waitFor(() => expect(screen.getByText('Brouillon — version 4')).toBeTruthy())

  it('un brouillon s’annonce comme tel et laisse ajouter un bloc', async () => {
    monter()
    await attendreOuverture()

    expect(palette().disabled).toBe(false)
    expect(bouton(/^Publier$/).disabled).toBe(false)
    expect(monterLaSeconde().disabled).toBe(false)
    expect(descendreLaPremiere().disabled).toBe(false)
    expect(boutons(/^Supprimer$/)[0]?.disabled).toBe(false)
  })

  it('une page vide ne peut pas être publiée', async () => {
    apiDashboard.blocs.mockResolvedValue([])
    monter()
    await attendreOuverture()

    expect(bouton(/^Publier$/).disabled).toBe(true)
  })

  it('publier change ce que l’éditeur dit de la version qu’il tient', async () => {
    monter()
    await attendreOuverture()

    bouton(/^Publier$/).click()

    // Sans cela, le bandeau continuait d'annoncer « Brouillon — version 4 »
    // alors que la base venait de la figer.
    await waitFor(() => expect(screen.getByText('Version 4 publiée')).toBeTruthy())
    expect(screen.queryByText('Brouillon — version 4')).toBeNull()
  })

  it('une version publiée ne propose plus aucune écriture', async () => {
    monter()
    await attendreOuverture()
    bouton(/^Publier$/).click()
    await waitFor(() => expect(screen.getByText('Version 4 publiée')).toBeTruthy())

    // Chacun de ces boutons visait une version que le domaine refuse d'écrire.
    expect(screen.queryByRole('button', { name: /^Publier$/ })).toBeNull()
    expect(palette().disabled).toBe(true)
    expect(monterLaSeconde().disabled).toBe(true)
    expect(descendreLaPremiere().disabled).toBe(true)
    expect(boutons(/^Supprimer$/)[0]?.disabled).toBe(true)
  })

  it('reprendre l’édition ouvre un nouveau brouillon, et c’est LUI qu’on édite', async () => {
    apiDashboard.ouvrirBrouillon
      .mockResolvedValueOnce(BROUILLON_4)
      .mockResolvedValueOnce(BROUILLON_5)
    monter()
    await attendreOuverture()
    bouton(/^Publier$/).click()
    await waitFor(() => expect(screen.getByText('Version 4 publiée')).toBeTruthy())

    bouton(/Reprendre/).click()
    await waitFor(() => expect(screen.getByText('Brouillon — version 5')).toBeTruthy())

    expect(apiDashboard.ouvrirBrouillon).toHaveBeenCalledTimes(2)

    // Le point réel : les lignes affichées doivent être celles de v5. Relire
    // v4 — l'identifiant capturé au rendu — afficherait des blocs figés dont
    // chaque bouton part en 409.
    boutons(/^Supprimer$/)[0]?.click()
    await waitFor(() => expect(apiDashboard.supprimerBloc).toHaveBeenCalledTimes(1))
    expect(apiDashboard.supprimerBloc.mock.calls[0]?.[1]).toBe('v5-a')
  })
})
