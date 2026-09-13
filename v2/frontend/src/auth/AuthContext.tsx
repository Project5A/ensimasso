import { User, UserManager } from 'oidc-client-ts'
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { parametresOidc } from './config'

type EtatAuth = {
  utilisateur: User | null
  chargement: boolean
  connecter: () => Promise<void>
  deconnecter: () => Promise<void>
  jeton: () => string | null
}

const Contexte = createContext<EtatAuth | null>(null)

/**
 * L'échange du code d'autorisation, partagé entre les montages.
 *
 * React monte, démonte et remonte chaque composant sous StrictMode : l'effet
 * partait deux fois, et un code d'autorisation OAuth ne s'échange qu'UNE fois.
 * Le second appel échouait en `invalid_grant` ; comme le nettoyage de
 * StrictMode avait entre-temps marqué le premier passage « plus vivant », son
 * résultat — le bon — était jeté et c'est l'échec du second qui l'emportait.
 * La connexion était impossible en développement.
 *
 * Une variable de module, et non une `ref` : les deux montages sont deux
 * instances distinctes du composant, et il faut qu'ils attendent la MÊME
 * promesse. Remise à zéro en cas d'échec, pour qu'un retour ultérieur avec un
 * code neuf ne se voie pas resservir un rejet.
 */
let echangeDuCode: Promise<User> | null = null

export function useAuth(): EtatAuth {
  const c = useContext(Contexte)
  if (!c) throw new Error('useAuth doit être utilisé dans <FournisseurAuth>')
  return c
}

export function FournisseurAuth({ children }: { children: ReactNode }) {
  const gestionnaire = useMemo(() => new UserManager(parametresOidc), [])
  const naviguer = useNavigate()
  const [utilisateur, setUtilisateur] = useState<User | null>(null)
  const [chargement, setChargement] = useState(true)

  useEffect(() => {
    let vivant = true

    async function amorcer() {
      try {
        // Retour du fournisseur d'identité : on échange le code contre un jeton.
        if (window.location.pathname === '/connexion/retour') {
          echangeDuCode ??= gestionnaire.signinRedirectCallback().catch((e) => {
            echangeDuCode = null
            throw e
          })
          const u = await echangeDuCode
          if (!vivant) return
          setUtilisateur(u)
          // On nettoie l'URL : le code d'autorisation n'a rien à faire dans
          // l'historique du navigateur.
          //
          // `navigate` et non `window.history.replaceState` : ce dernier change
          // la barre d'adresse sans prévenir React Router, qui continuait donc
          // d'afficher la route /connexion/retour — une page vide — sous une
          // URL qui annonçait le tableau de bord.
          naviguer(sessionStorage.getItem('retour') ?? '/tableau', { replace: true })
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
  }, [gestionnaire, naviguer])

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
