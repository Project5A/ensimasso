import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Le retour du fournisseur d'identité, sous StrictMode.
 *
 * React monte, démonte et remonte chaque composant en développement. L'effet
 * qui échange le code d'autorisation contre un jeton part donc DEUX fois, et
 * un code d'autorisation OAuth ne s'échange qu'UNE fois : le second appel
 * échoue en `invalid_grant`. Comme le premier passage a été marqué « plus
 * vivant » par le nettoyage de StrictMode, son résultat — le bon — était jeté,
 * et c'est l'échec du second qui s'imposait.
 */
const appels = { callback: 0 }
let utilisateurRendu: unknown = null

vi.mock('oidc-client-ts', () => {
  class UserManager {
    events = {
      addAccessTokenExpired: () => {},
      removeAccessTokenExpired: () => {},
      addUserLoaded: () => {},
      removeUserLoaded: () => {},
    }
    async signinRedirectCallback() {
      appels.callback += 1
      if (appels.callback > 1) {
        // Ce que fait un vrai serveur OAuth au second usage du même code.
        throw new Error('invalid_grant: authorization code already redeemed')
      }
      return utilisateurRendu
    }
    async getUser() { return null }
    async signinRedirect() {}
    async signoutRedirect() {}
  }
  return { UserManager, User: class {} }
})

vi.mock('../config', () => ({ parametresOidc: {} }))

const { FournisseurAuth, useAuth } = await import('../AuthContext')

function Sonde() {
  const { utilisateur, chargement } = useAuth()
  if (chargement) return <span>chargement</span>
  return <span>{utilisateur ? 'connecté' : 'déconnecté'}</span>
}

/** Affiche la route que React Router estime être la route courante. */
function RouteCourante() {
  return <span data-testid="route">{useLocation().pathname}</span>
}

describe('retour du fournisseur d’identité', () => {
  beforeEach(() => {
    appels.callback = 0
    utilisateurRendu = { access_token: 'jeton', expired: false }
    window.history.replaceState({}, '', '/connexion/retour?code=abc&state=xyz')
    sessionStorage.clear()
  })

  afterEach(cleanup)

  it("sous StrictMode, le code n'est échangé qu'une fois et la connexion aboutit", async () => {
    render(
      <StrictMode>
        <MemoryRouter>
          <FournisseurAuth>
            <Sonde />
          </FournisseurAuth>
        </MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(screen.getByText('connecté')).toBeTruthy())

    // Et une seule présentation du code : le second échange échouerait, mais
    // surtout il n'a rien à faire là — c'est un jeton d'usage unique.
    expect(appels.callback).toBe(1)
  })

  it("après le retour, le ROUTEUR quitte la page de callback", async () => {
    sessionStorage.setItem('retour', '/tableau/contenu')

    render(
      <MemoryRouter initialEntries={['/connexion/retour?code=abc&state=xyz']}>
        <FournisseurAuth>
          <Sonde />
          <Routes>
            <Route path="*" element={<RouteCourante />} />
          </Routes>
        </FournisseurAuth>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('connecté')).toBeTruthy())

    // `window.history.replaceState` changeait la barre d'adresse sans prévenir
    // React Router : l'URL annonçait le tableau de bord pendant que la route
    // rendue restait /connexion/retour, c'est-à-dire une page vide.
    await waitFor(() =>
      expect(screen.getByTestId('route').textContent).toBe('/tableau/contenu'),
    )
  })
})
