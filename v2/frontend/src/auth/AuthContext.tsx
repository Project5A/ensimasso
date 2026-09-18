import { User, UserManager } from 'oidc-client-ts'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { parametresOidc } from './config'

type EtatAuth = {
  utilisateur: User | null
  chargement: boolean
  /**
   * Pourquoi le retour du fournisseur d'identité a échoué, s'il a échoué.
   *
   * Cet échec était avalé par un `catch` muet et rendu comme une simple
   * absence de session : l'écran affichait « Connexion requise », c'est-à-dire
   * le message de quelqu'un qui n'a jamais essayé de se connecter. Un refus
   * explicite du fournisseur — droits non accordés, compte désactivé — n'était
   * donc jamais dit, et le bouton proposé relançait exactement la manœuvre qui
   * venait d'échouer.
   */
  erreurConnexion: string | null
  connecter: () => Promise<void>
  deconnecter: () => Promise<void>
  jeton: () => string | null
}

/** La route de retour du fournisseur d'identité. */
const CHEMIN_RETOUR = '/connexion/retour'

/**
 * Un chemin INTERNE, et rien d'autre.
 *
 * <p>Le motif ne se contente pas d'exiger un « / » initial. `//evil.example`
 * et `/\\evil.example` sont des URL protocole-relatives : le navigateur les
 * comprend comme un autre HÔTE, et une redirection ouverte est exactement ce
 * qu'on offre à qui veut faire passer sa page de phishing pour la nôtre —
 * l'utilisateur vient de cliquer « se connecter », il s'attend à être renvoyé
 * chez nous.
 *
 * <p>Ce n'est pas théorique ici : react-router 6 porte l'avis
 * GHSA-wrjc-x8rr-h8h6, « open redirect via backslash dans Link et
 * useNavigate ». Cette garde tient indépendamment de la version de la
 * bibliothèque, ce qui est le point : on ne veut pas que la sûreté d'une
 * redirection dépende d'un correctif tiers.
 */
const CHEMIN_INTERNE = /^\/(?![/\\])[A-Za-z0-9\-._~!$&'()*+,;=:@%/]*$/

/**
 * Où revenir après connexion — jamais la page de retour elle-même, et jamais
 * ailleurs que chez nous.
 *
 * `connecter()` enregistrait `window.location.pathname` sans le regarder. Or
 * le bouton « Se connecter » est aussi celui qu'on voit APRÈS un retour raté,
 * donc depuis /connexion/retour : la valeur enregistrée devenait la page de
 * retour. La connexion suivante y ramenait, l'amorçage y relançait l'échange
 * d'un code qui n'existe plus, et l'échec ramenait au même bouton. La clé
 * n'étant jamais effacée, la boucle tenait toute la session.
 */
function destinationApresConnexion(chemin: string): string {
  if (!CHEMIN_INTERNE.test(chemin)) return '/tableau'
  return chemin.startsWith(CHEMIN_RETOUR) ? '/tableau' : chemin
}

/** Ce que le fournisseur dit de son refus, quand il le dit lui-même. */
function motifDeRefus(recherche: string): string | null {
  const params = new URLSearchParams(recherche)
  const code = params.get('error')
  if (!code) return null
  const description = params.get('error_description')
  return description ? `${code} — ${description}` : code
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
  const [erreurConnexion, setErreurConnexion] = useState<string | null>(null)

  // `naviguer` dans une référence, et non dans les dépendances de l'effet.
  //
  // `useNavigate()` rend une fonction dont l'identité CHANGE à chaque
  // changement de route. L'effet d'amorçage repartait donc après sa propre
  // navigation, retombait dans la branche « retour du fournisseur » — le
  // chemin de `window.location` n'ayant pas encore suivi — et rejouait un
  // échange de code déjà consommé. Le défaut restait invisible tant que la
  // clé `retour` n'était jamais effacée : la seconde navigation refaisait la
  // première. Elle l'est désormais, et la seconde partait vers /tableau.
  const refNaviguer = useRef(naviguer)
  refNaviguer.current = naviguer

  useEffect(() => {
    let vivant = true

    async function amorcer() {
      try {
        // Retour du fournisseur d'identité : on échange le code contre un jeton.
        if (window.location.pathname === CHEMIN_RETOUR) {
          // Un refus annoncé par le fournisseur n'est pas une panne à
          // rattraper : il est dit tel quel, avec ses propres mots.
          const refus = motifDeRefus(window.location.search)
          if (refus) {
            if (vivant) {
              setUtilisateur(null)
              setErreurConnexion(refus)
            }
            return
          }
          echangeDuCode ??= gestionnaire.signinRedirectCallback().catch((e) => {
            echangeDuCode = null
            throw e
          })
          const u = await echangeDuCode
          if (!vivant) return
          setUtilisateur(u)
          setErreurConnexion(null)
          // On nettoie l'URL : le code d'autorisation n'a rien à faire dans
          // l'historique du navigateur.
          //
          // `navigate` et non `window.history.replaceState` : ce dernier change
          // la barre d'adresse sans prévenir React Router, qui continuait donc
          // d'afficher la route /connexion/retour — une page vide — sous une
          // URL qui annonçait le tableau de bord.
          const voulu = sessionStorage.getItem('retour')
          // Consommée : sans cela, toute connexion ultérieure de la session
          // repartait vers une page choisie une fois, il y a longtemps.
          sessionStorage.removeItem('retour')
          refNaviguer.current(destinationApresConnexion(voulu ?? '/tableau'), { replace: true })
          return
        }
        const u = await gestionnaire.getUser()
        if (vivant) setUtilisateur(u && !u.expired ? u : null)
      } catch (e: unknown) {
        if (!vivant) return
        setUtilisateur(null)
        // Seul le retour raté est une erreur DE CONNEXION. Ailleurs, l'absence
        // de session est l'état normal d'un visiteur.
        if (window.location.pathname === CHEMIN_RETOUR) {
          setErreurConnexion(e instanceof Error ? e.message : 'échange du code impossible')
        }
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
    setErreurConnexion(null)
    sessionStorage.setItem('retour', destinationApresConnexion(window.location.pathname))
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
    () => ({ utilisateur, chargement, erreurConnexion, connecter, deconnecter, jeton }),
    [utilisateur, chargement, erreurConnexion, connecter, deconnecter, jeton],
  )

  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>
}
