import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
const { Protege } = await import('../Protege')

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

  // ------------------------------------------------- quand le retour échoue

  it('un refus du fournisseur est DIT, avec son motif, et rien n’est échangé', async () => {
    window.history.replaceState({}, '', 
      '/connexion/retour?error=access_denied&error_description=Compte+non+autoris%C3%A9')

    render(
      <MemoryRouter initialEntries={['/connexion/retour']}>
        <FournisseurAuth>
          <Protege><span>tableau de bord</span></Protege>
        </FournisseurAuth>
      </MemoryRouter>,
    )

    // « Connexion requise » est le message de quelqu'un qui n'a pas essayé.
    // Celui-ci vient d'essayer, et s'est fait refuser.
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /connexion a échoué/i })).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toContain('access_denied')
    expect(screen.getByRole('alert').textContent).toContain('Compte non autorisé')
    expect(screen.queryByText('Connexion requise')).toBeNull()

    // Et on ne présente pas un code au fournisseur quand il vient de dire non.
    expect(appels.callback).toBe(0)
  })

  it('réessayer depuis la page de retour ne fait pas revenir SUR la page de retour', async () => {
    window.history.replaceState({}, '', '/connexion/retour?error=access_denied')

    render(
      <MemoryRouter initialEntries={['/connexion/retour']}>
        <FournisseurAuth>
          <Protege><span>tableau de bord</span></Protege>
        </FournisseurAuth>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByRole('button', { name: /réessayer/i })).toBeTruthy())
    await userEvent.click(screen.getByRole('button', { name: /réessayer/i }))

    // Le piège : `connecter()` enregistrait window.location.pathname sans le
    // regarder, et ce bouton se clique depuis /connexion/retour. La connexion
    // suivante y revenait, l'amorçage y rejouait un échange de code qui
    // n'existe plus, et l'échec ramenait au même bouton — toute la session,
    // la clé n'étant jamais effacée.
    expect(sessionStorage.getItem('retour')).toBe('/tableau')
  })

  it('la destination de retour est CONSOMMÉE : la connexion suivante ne la resservira pas', async () => {
    sessionStorage.setItem('retour', '/tableau/contenu')

    render(
      <MemoryRouter initialEntries={['/connexion/retour?code=abc&state=xyz']}>
        <FournisseurAuth>
          <Sonde />
        </FournisseurAuth>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('connecté')).toBeTruthy())
    expect(sessionStorage.getItem('retour')).toBeNull()
  })

  it('une destination de retour qui SORT du site est refusée', async () => {
    // react-router 6 porte GHSA-wrjc-x8rr-h8h6 : « open redirect via
    // backslash dans Link et useNavigate ». Mais la garde est ici, et non
    // dans l'attente d'un correctif tiers : une redirection ouverte sert à
    // faire passer une page de phishing pour la nôtre, juste après que
    // l'utilisateur a cliqué « se connecter ».
    for (const hostile of [
      '//evil.example/x',
      '/\\\\evil.example/x',
      'https://evil.example/x',
      'javascript:alert(1)',
    ]) {
      sessionStorage.clear()
      sessionStorage.setItem('retour', hostile)
      appels.callback = 0

      const vue = render(
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
      await waitFor(() => expect(screen.getByTestId('route').textContent).toBe('/tableau'))
      vue.unmount()
    }
  })

  it('mais un chemin interne normal est bien suivi', async () => {
    sessionStorage.setItem('retour', '/tableau/mandats/m1/agenda')

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

    // Sans ce cas, la garde ci-dessus serait satisfaite par un code qui
    // renverrait TOUJOURS vers /tableau, et l'écran d'où l'on vient serait
    // perdu à chaque connexion.
    await waitFor(() =>
      expect(screen.getByTestId('route').textContent).toBe('/tableau/mandats/m1/agenda'))
  })
})
