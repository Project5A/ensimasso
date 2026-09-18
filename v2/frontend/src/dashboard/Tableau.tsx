import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiDashboard, ErreurApi, type PosteVue } from '../api'
import { useAuth } from '../auth/AuthContext'

const LIBELLE_POSTE: Record<string, string> = {
  PRESIDENT: 'Président·e', VICE_PRESIDENT: 'Vice-président·e',
  TRESORIER: 'Trésorier·ère', SECRETAIRE: 'Secrétaire',
  RESP_COM: 'Responsable communication', RESP_EVENEMENTS: 'Responsable évènements',
  MEMBRE_BUREAU: 'Membre du bureau',
}

/**
 * Le point d'entrée du tableau de bord : les mandats où l'utilisateur a un poste.
 *
 * <p>Ces postes sont lus depuis l'API, jamais depuis le jeton. Un jeton qui
 * porterait « président du BDE 2025-2026, trésorier du Gala 2025-2026 » serait
 * volumineux, périmé dès qu'un rôle change, et impossible à révoquer en cours
 * de session.
 *
 * <p>Le bureau ENTRANT y figure aussi. Il n'y figurait pas : la requête ne
 * rendait que les mandats EN_FONCTION, et cet écran est le seul chemin vers un
 * mandat. Un bureau tout juste désigné lisait donc « Aucun mandat en cours » —
 * et, en dessous, qu'il devait se faire désigner. Toute la préparation d'un
 * bureau entrant, qui est précisément l'objet du statut PRÉPARATION, n'était
 * atteignable qu'en tapant un UUID à la main.
 */
export default function Tableau() {
  const { jeton, deconnecter, utilisateur } = useAuth()
  const [postes, setPostes] = useState<PosteVue[] | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    apiDashboard
      .mesPostes(jeton())
      .then((p) => { if (!ctrl.signal.aborted) setPostes(p) })
      .catch((e: unknown) => {
        if (!ctrl.signal.aborted) {
          setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible')
        }
      })
    return () => ctrl.abort()
  }, [jeton])

  return (
    <main className="page page--centree">
      <header className="tableau__entete">
        <div>
          <h1>Tableau de bord</h1>
          <p className="aide">
            Connecté en tant que {utilisateur?.profile.preferred_username ?? utilisateur?.profile.email}
          </p>
        </div>
        <button className="lien" onClick={() => void deconnecter()}>Se déconnecter</button>
      </header>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!postes && !erreur && <p aria-live="polite">Chargement…</p>}

      {postes?.length === 0 && (
        <div className="vide-etat">
          <h2>Aucun mandat</h2>
          <p>
            Vous n'occupez de poste dans aucun bureau, ni en fonction ni en
            préparation. Si vous venez d'être élu·e, le président sortant doit
            vous désigner dans la passation.
          </p>
        </div>
      )}

      {postes && postes.length > 0 && (
        <ul className="mandats">
          {postes.map((p) => (
            <li key={p.mandatId}>
              <Link to={`/tableau/mandats/${p.mandatId}`}>
                <span className="mandats__annee">{p.anneeCode}</span>
                <span className="mandats__poste">{LIBELLE_POSTE[p.poste] ?? p.poste}</span>
                {/* Sans cette mention, le mandat entrant et le mandat en
                    fonction s'affichent à l'identique : on ne sait plus
                    lequel des deux est déjà en ligne. */}
                {p.statutMandat === 'PREPARATION' && (
                  <span className="mandats__statut">Bureau entrant — en préparation</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
