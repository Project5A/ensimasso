import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiDashboard, ErreurApi, type AdhesionVue, type CommandeVue } from '../api'
import { useAuth } from '../auth/AuthContext'
import { euros } from './Adherer'

const LIBELLE_COMMANDE: Record<CommandeVue['statut'], string> = {
  OUVERTE: 'À payer', PAYEE: 'Payée', ANNULEE: 'Annulée', REMBOURSEE: 'Remboursée',
}

const LIBELLE_ADHESION: Record<AdhesionVue['statut'], string> = {
  EN_ATTENTE_PAIEMENT: 'En attente de paiement', ACTIVE: 'Active',
  ANNULEE: 'Annulée', REMBOURSEE: 'Remboursée',
}

/**
 * Mes commandes et mes adhésions.
 *
 * <p>C'est la sortie de secours qui manquait : une commande dont le paiement
 * a échoué restait payable — son intention existe toujours — mais rien ne
 * permettait de la retrouver. Sans cet écran, l'onglet fermé par mégarde
 * emportait le seul chemin vers le paiement.
 */
export default function MesCommandes() {
  const { jeton, utilisateur, chargement, connecter } = useAuth()

  const refJeton = useRef(jeton)
  refJeton.current = jeton

  // `connecte` plutôt que `utilisateur` dans les dépendances : l'effet ne
  // s'intéresse qu'au FAIT d'être connecté, et un objet change d'identité à
  // chaque rendu — l'effet se rappellerait alors sans fin, puisqu'il écrit
  // dans l'état. Même précaution que pour `jeton`.
  const connecte = utilisateur != null

  const [commandes, setCommandes] = useState<CommandeVue[] | null>(null)
  const [adhesions, setAdhesions] = useState<AdhesionVue[]>([])
  const [erreur, setErreur] = useState<string | null>(null)

  useEffect(() => {
    if (!connecte) return
    let vivant = true
    Promise.all([
      apiDashboard.mesCommandes(refJeton.current()),
      apiDashboard.mesAdhesions(refJeton.current()),
    ])
      .then(([c, a]) => {
        if (!vivant) return
        setCommandes(c)
        setAdhesions(a)
      })
      .catch((e: unknown) => {
        if (vivant) setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible')
      })
    return () => { vivant = false }
  }, [connecte])

  if (chargement) {
    return <main className="page page--centree"><p aria-live="polite">Chargement…</p></main>
  }

  if (!utilisateur) {
    return (
      <main className="page page--centree">
        <h1>Mes adhésions</h1>
        <button className="bouton" onClick={() => void connecter()}>Se connecter</button>
      </main>
    )
  }

  return (
    <main className="page page--centree">
      <h1>Mes adhésions</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!commandes && !erreur && <p aria-live="polite">Chargement…</p>}

      <section>
        <h2>Adhésions</h2>
        {adhesions.length === 0 ? (
          <p className="aide">Vous n'avez adhéré à aucune association.</p>
        ) : (
          <ul className="mandats">
            {adhesions.map((a) => (
              <li key={a.id}>
                <span className="mandats__annee">{a.couvreAnneeCode}</span>
                <span className="mandats__poste">
                  {LIBELLE_ADHESION[a.statut]} — {euros(a.montantPayeCents)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {commandes && commandes.length > 0 && (
        <section>
          <h2>Commandes</h2>
          <ul className="mandats">
            {commandes.map((c) => (
              <li key={c.id}>
                {c.statut === 'OUVERTE' ? (
                  <Link to={`/commandes/${c.id}`}>
                    <span className="mandats__annee">{euros(c.montantTotalCents)}</span>
                    <span className="mandats__poste">Reprendre le paiement</span>
                  </Link>
                ) : (
                  <>
                    <span className="mandats__annee">{euros(c.montantTotalCents)}</span>
                    <span className="mandats__poste">{LIBELLE_COMMANDE[c.statut]}</span>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}
