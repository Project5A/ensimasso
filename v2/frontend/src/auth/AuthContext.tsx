import { User, UserManager } from 'oidc-client-ts'
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { parametresOidc } from './config'

type EtatAuth = {
  utilisateur: User | null
  chargement: boolean
  connecter: () => Promise<void>
  deconnecter: () => Promise<void>
  jeton: () => string | null
}

const Contexte = createContext<EtatAuth | null>(null)

export function useAuth(): EtatAuth {
  const c = useContext(Contexte)
  if (!c) throw new Error('useAuth doit être utilisé dans <FournisseurAuth>')
  return c
}

export function FournisseurAuth({ children }: { children: ReactNode }) {
  const gestionnaire = useMemo(() => new UserManager(parametresOidc), [])
  const [utilisateur, setUtilisateur] = useState<User | null>(null)
  const [chargement, setChargement] = useState(true)

  useEffect(() => {
    let vivant = true

    async function amorcer() {
      try {
        // Retour du fournisseur d'identité : on échange le code contre un jeton.
        if (window.location.pathname === '/connexion/retour') {
          const u = await gestionnaire.signinRedirectCallback()
          if (!vivant) return
          setUtilisateur(u)
          // On nettoie l'URL : le code d'autorisation n'a rien à faire dans
          // l'historique du navigateur.
          window.history.replaceState({}, '', sessionStorage.getItem('retour') ?? '/tableau')
          return
        }
        const u = await gestionnaire.getUser()
        if (vivant) setUtilisateur(u && !u.expired ? u : null)
      } catch {
        if (vivant) setUtilisateur(null)
      } finally {
        if (vivant) setChargement(false)
      }
    }

    void amorcer()

    const surExpiration = () => setUtilisateur(null)
    gestionnaire.events.addAccessTokenExpired(surExpiration)
    const surRenouvellement = (u: User) => setUtilisateur(u)
    gestionnaire.events.addUserLoaded(surRenouvellement)

    return () => {
      vivant = false
      gestionnaire.events.removeAccessTokenExpired(surExpiration)
      gestionnaire.events.removeUserLoaded(surRenouvellement)
    }
  }, [gestionnaire])

  const connecter = useCallback(async () => {
    sessionStorage.setItem('retour', window.location.pathname)
    await gestionnaire.signinRedirect()
  }, [gestionnaire])

  const deconnecter = useCallback(async () => {
    setUtilisateur(null)
    await gestionnaire.signoutRedirect()
  }, [gestionnaire])

  // Lu à chaque requête plutôt que capturé : un jeton renouvelé en arrière-plan
  // doit être utilisé immédiatement.
  const jeton = useCallback(
    () => (utilisateur && !utilisateur.expired ? utilisateur.access_token : null),
    [utilisateur],
  )

  const valeur = useMemo<EtatAuth>(
    () => ({ utilisateur, chargement, connecter, deconnecter, jeton }),
    [utilisateur, chargement, connecter, deconnecter, jeton],
  )

  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>
}
