import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi, type JournalVue } from '../api'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

function euros(cents: number): string {
  return `${(cents / 100).toFixed(2).replace('.', ',')} €`
}

/**
 * Le journal comptable d'une association.
 *
 * <p>Le module `tresorerie` était complet — commandes, encaissements vérifiés
 * contre le montant dû, remboursements, journal append-only garanti par un
 * trigger — et aucun écran ne l'appelait. Un trésorier ne pouvait pas lire son
 * propre solde.
 *
 * <p>Chaque ligne est immuable : le journal ne se corrige pas en effaçant, on y
 * ajoute. Un remboursement écrit une SORTIE en face de l'ENTRÉE, et les deux
 * restent lisibles — c'est ce qui permet de répondre à « où est passé cet
 * argent ? » un an plus tard.
 */
export default function Tresorerie() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const refJeton = useRef(jeton)
  refJeton.current = jeton

  const [associationId, setAssociationId] = useState<string | null>(null)
  const [journal, setJournal] = useState<JournalVue | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  useEffect(() => {
    apiDashboard
      .mesPostes(refJeton.current())
      .then((postes) => {
        const p = postes.find((x) => x.mandatId === mandatId)
        if (p) setAssociationId(p.associationId)
        else setErreur("Ce mandat n'est pas le vôtre.")
      })
      .catch((e: unknown) =>
        setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [mandatId])

  const charger = useCallback(async () => {
    if (!associationId) return
    setJournal(await apiDashboard.journal(refJeton.current(), associationId))
  }, [associationId])

  useEffect(() => {
    if (!associationId) return
    charger().catch((e: unknown) =>
      setErreur(e instanceof ErreurApi
        ? e.message
        : 'Service indisponible'))
  }, [associationId, charger])

  return (
    <main className="page">
      <FilMandat section="Trésorerie" />
      <NavMandat mandatId={mandatId} actif="tresorerie" />

      <h1>Trésorerie</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!journal && !erreur && <p aria-live="polite">Chargement…</p>}

      {journal && (
        <>
          <p className="solde">
            Solde : <strong>{euros(journal.soldeCents)}</strong>
          </p>
          <p className="aide">
            Réservé au président et au trésorier. Le journal est en ajout seul —
            un trigger de la base refuse toute modification d'une écriture
            passée : on n'y corrige rien, on y ajoute.
          </p>

          {journal.ecritures.length === 0 ? (
            <p className="aide">Aucune écriture pour le moment.</p>
          ) : (
            <table className="journal">
              <thead>
                <tr>
                  <th scope="col">Sens</th>
                  <th scope="col">Montant</th>
                  <th scope="col">Motif</th>
                </tr>
              </thead>
              <tbody>
                {journal.ecritures.map((e) => (
                  <tr key={e.id}>
                    <td>{e.sens === 'ENTREE' ? 'Entrée' : 'Sortie'}</td>
                    <td className="journal__montant">
                      {e.sens === 'ENTREE' ? '+' : '−'} {euros(e.montantCents)}
                    </td>
                    <td>{e.motif}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </main>
  )
}
