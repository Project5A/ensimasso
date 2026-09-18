import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CampagneVue, TarifVue } from '../../api'

const apiDashboard = {
  associations: vi.fn(),
  campagnes: vi.fn(),
  tarifs: vi.fn(),
  commanderAdhesion: vi.fn(),
  adhererGratuitement: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    jeton: () => 'jeton-de-test',
    utilisateur: { profile: { sub: 'p1' } },
    chargement: false,
    connecter: vi.fn(),
  }),
}))

const { default: Adherer } = await import('../Adherer')

const campagne = (p: Partial<CampagneVue> = {}): CampagneVue => ({
  id: 'c1', associationId: 'a1', couvreAnneeCode: '2026-2027',
  statut: 'OUVERTE', ouvreLe: null, fermeLe: null,
  ...p,
})

const tarif = (p: Partial<TarifVue> = {}): TarifVue => ({
  id: 't1', libelle: 'Année complète', montantCents: 1500, publicCible: 'ETUDIANT',
  ...p,
})

function Route_() { return <span data-testid="route">{useLocation().pathname}</span> }

function monter() {
  return render(
    <MemoryRouter initialEntries={['/assos/bde/adherer']}>
      <Routes>
        <Route path="/assos/:slug/adherer" element={<Adherer />} />
        <Route path="*" element={<span />} />
      </Routes>
      <Route_ />
    </MemoryRouter>,
  )
}

/**
 * Adhérer, depuis le bouton du portail public.
 *
 * <p>Le bloc CTA_ADHESION pointe vers /assos/{slug}/adherer depuis toujours,
 * et cette route n'existait pas : elle tombait sur la recherche d'une page
 * nommée « adherer ». Le seul bouton d'adhésion du site ne menait nulle part.
 */
describe('Adhérer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.associations.mockResolvedValue([
      { id: 'a1', slug: 'bde', nom: 'Bureau des Élèves', type: 'BUREAU' },
    ])
    apiDashboard.campagnes.mockResolvedValue([campagne()])
    apiDashboard.tarifs.mockResolvedValue([tarif()])
  })
  afterEach(cleanup)

  it('un tarif PAYANT passe par la commande, seule porte qui crée le paiement', async () => {
    apiDashboard.commanderAdhesion.mockResolvedValue({
      id: 'cmd1', associationId: 'a1', statut: 'OUVERTE',
      montantTotalCents: 1500, devise: 'EUR', payeeLe: null,
    })
    monter()

    await userEvent.click(await screen.findByRole('button', { name: /15,00/ }))

    // L'autre route ne crée ni commande ni intention : une adhésion payante
    // créée par là ne pourrait ni être payée ni être abandonnée.
    await waitFor(() => expect(apiDashboard.commanderAdhesion)
      .toHaveBeenCalledWith(expect.anything(), 'c1', 'ETUDIANT'))
    expect(apiDashboard.adhererGratuitement).not.toHaveBeenCalled()
    // Et l'on part vers le paiement de CETTE commande.
    await waitFor(() =>
      expect(screen.getByTestId('route').textContent).toBe('/commandes/cmd1'))
  })

  it('aucun montant ne part du navigateur : le prix vient des tarifs', async () => {
    apiDashboard.commanderAdhesion.mockResolvedValue({
      id: 'cmd1', associationId: 'a1', statut: 'OUVERTE',
      montantTotalCents: 1500, devise: 'EUR', payeeLe: null,
    })
    monter()
    await userEvent.click(await screen.findByRole('button', { name: /15,00/ }))

    await waitFor(() => expect(apiDashboard.commanderAdhesion).toHaveBeenCalled())
    // C'était la faille PAY-03 de la v1 : poster {"amount":1} suffisait à tout
    // payer un centime. Le corps ne porte qu'un public cible.
    const args = apiDashboard.commanderAdhesion.mock.calls[0] as unknown[]
    expect(JSON.stringify(args)).not.toContain('1500')
  })

  it('un tarif GRATUIT s’active tout de suite, sans commande', async () => {
    apiDashboard.tarifs.mockResolvedValue([tarif({ montantCents: 0, libelle: 'Gratuit' })])
    apiDashboard.adhererGratuitement.mockResolvedValue({})
    monter()

    await userEvent.click(await screen.findByRole('button', { name: /gratuit/i }))

    // Rien à encaisser : ouvrir une commande à zéro euro n'aurait aucun objet.
    await waitFor(() => expect(apiDashboard.adhererGratuitement).toHaveBeenCalled())
    expect(apiDashboard.commanderAdhesion).not.toHaveBeenCalled()
    expect(await screen.findByRole('status')).toBeTruthy()
  })

  it('sans campagne ouverte, le dit au lieu de proposer un bouton mort', async () => {
    apiDashboard.campagnes.mockResolvedValue([campagne({ statut: 'FERMEE' })])
    monter()

    expect(await screen.findByText(/aucune campagne ouverte/i)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /15,00/ })).toBeNull()
  })

  it('une campagne sans tarif ne laisse pas cliquer dans le vide', async () => {
    apiDashboard.tarifs.mockResolvedValue([])
    monter()

    expect(await screen.findByText(/aucun tarif/i)).toBeTruthy()
  })
})
