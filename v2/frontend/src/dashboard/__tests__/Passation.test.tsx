import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MembreVue, PassationVue } from '../../api'

const apiDashboard = {
  mesPostes: vi.fn(),
  passations: vi.fn(),
  bureau: vi.fn(),
  preparerPassation: vi.fn(),
  designer: vi.fn(),
  bureauComplet: vi.fn(),
  activerPassation: vi.fn(),
  annulerPassation: vi.fn(),
}

vi.mock('../../api', async (original) => {
  const reel = await original<typeof import('../../api')>()
  return { ...reel, apiDashboard }
})
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    jeton: () => 'jeton-de-test',
    utilisateur: { profile: { sub: '11111111-1111-1111-1111-111111111111' } },
  }),
}))

const { default: Passation } = await import('../Passation')

const passation = (p: Partial<PassationVue> = {}): PassationVue => ({
  id: 'ps1', associationId: 'a1',
  mandatSortantId: 'm1', mandatEntrantId: 'm2',
  statut: 'PREPAREE', pagesClonees: 3,
  ...p,
})

const membre = (p: Partial<MembreVue> = {}): MembreVue => ({
  id: 'mb1', personneId: '22222222-2222-2222-2222-222222222222',
  poste: 'PRESIDENT', titreAffiche: null, ordre: 0, photoMediaKey: null,
  ...p,
})

function monter() {
  return render(
    <MemoryRouter initialEntries={['/tableau/mandats/m1/passation']}>
      <Routes>
        <Route path="/tableau/mandats/:mandatId/passation" element={<Passation />} />
      </Routes>
    </MemoryRouter>,
  )
}

/**
 * La passation : quatre routes qui faisaient avancer, aucune qui lisait, et
 * aucun écran pour les appeler. Tout le statut PRÉPARATION — le clonage des
 * pages publiées, le thème repris, le bureau entrant qui se compose avant l'AG
 * — ne servait à rien faute de ce chemin.
 */
describe('Passation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiDashboard.mesPostes.mockResolvedValue([
      { associationId: 'a1', mandatId: 'm1', anneeCode: '2025-2026',
        poste: 'PRESIDENT', statutMandat: 'EN_FONCTION' },
    ])
    apiDashboard.passations.mockResolvedValue([])
    apiDashboard.bureau.mockResolvedValue([])
  })
  afterEach(cleanup)

  it('sans passation en cours, propose de la préparer et envoie un instant zoné', async () => {
    apiDashboard.preparerPassation.mockResolvedValue(passation())
    monter()

    const debut = await screen.findByLabelText(/début prévu/i)
    await userEvent.type(debut, '2026-09-01T00:00')
    await userEvent.click(screen.getByRole('button', { name: 'Préparer' }))

    await waitFor(() => expect(apiDashboard.preparerPassation).toHaveBeenCalled())
    const [, asso, debutLe] = apiDashboard.preparerPassation.mock.calls[0] as
      [unknown, string, string, string | null]
    expect(asso).toBe('a1')
    // Le champ HTML ne porte aucun fuseau ; l'API attend un instant.
    expect(debutLe).toMatch(/Z$/)
  })

  it('une passation en cours montre son état, ses pages recopiées et le bureau entrant', async () => {
    apiDashboard.passations.mockResolvedValue([passation()])
    apiDashboard.bureau.mockResolvedValue([membre()])
    monter()

    expect(await screen.findByText(/le bureau entrant se compose/i)).toBeTruthy()
    expect(screen.getByText(/3 pages recopiées/)).toBeTruthy()
    // La lecture du bureau porte sur le mandat ENTRANT, pas sur le sortant :
    // c'est lui qu'on est en train de composer.
    expect(apiDashboard.bureau).toHaveBeenCalledWith(expect.anything(), 'm2')

    // On vise la LIGNE du bureau, pas le libellé « Président·e » en général :
    // il apparaît aussi dans la liste déroulante des postes à désigner, et une
    // assertion qui les confond passerait même si la ligne n'existait pas.
    const ligne = screen.getByText('22222222-2222-2222-2222-222222222222')
      .closest('li') as HTMLElement
    expect(ligne.textContent).toContain('Président·e')
  })

  it('désigner refuse un identifiant qui n’en est pas un, sans rien envoyer', async () => {
    apiDashboard.passations.mockResolvedValue([passation()])
    monter()

    const champ = await screen.findByLabelText(/identifiant du compte/i)
    await userEvent.type(champ, 'jean-michel')
    await userEvent.click(screen.getByRole('button', { name: 'Désigner' }))

    expect((await screen.findByRole('alert')).textContent).toContain('identifiant')
    expect(apiDashboard.designer).not.toHaveBeenCalled()
  })

  it('l’écran donne à chacun SON identifiant : il n’existe aucun annuaire', async () => {
    apiDashboard.passations.mockResolvedValue([passation()])
    monter()

    // Le système ne stocke aucun nom — `personne_id` est le « sub » Keycloak et
    // rien d'autre. Le dire, et donner à l'utilisateur de quoi se faire
    // désigner, vaut mieux qu'un champ vide qu'il ne peut pas remplir.
    expect(await screen.findByText('11111111-1111-1111-1111-111111111111')).toBeTruthy()
  })

  it('« bureau complet » n’apparaît qu’avant, et l’investiture qu’après', async () => {
    apiDashboard.passations.mockResolvedValue([passation({ statut: 'PREPAREE' })])
    const { unmount } = monter()
    await screen.findByRole('button', { name: /le bureau est complet/i })
    expect(screen.queryByRole('button', { name: /investir/i })).toBeNull()
    unmount()

    apiDashboard.passations.mockResolvedValue([passation({ statut: 'BUREAU_COMPLETE' })])
    monter()
    // Le serveur refuse l'investiture d'un bureau incomplet — il exige au moins
    // un président et un trésorier. L'écran ne propose donc pas un bouton qui
    // échouerait.
    expect(await screen.findByRole('button', { name: /investir/i })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /le bureau est complet/i })).toBeNull()
  })

  it('l’investiture envoie la date de l’AG', async () => {
    apiDashboard.passations.mockResolvedValue([passation({ statut: 'BUREAU_COMPLETE' })])
    apiDashboard.activerPassation.mockResolvedValue(passation({ statut: 'ACTIVEE' }))
    monter()

    await userEvent.type(await screen.findByLabelText(/assemblée générale/i), '2026-09-15T18:00')
    await userEvent.click(screen.getByRole('button', { name: /investir/i }))

    await waitFor(() => expect(apiDashboard.activerPassation).toHaveBeenCalled())
    const [, , aLAg] = apiDashboard.activerPassation.mock.calls[0] as
      [unknown, string, string | null]
    expect(aLAg).toMatch(/Z$/)
  })
})
