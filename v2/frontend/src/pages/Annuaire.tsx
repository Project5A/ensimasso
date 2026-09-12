import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErreurApi } from '../api'
import type { AssociationVue } from '../types'

const LIBELLE_TYPE: Record<string, string> = {
  BUREAU: 'Bureau',
  CLUB: 'Club',
  TECHNIQUE: 'Association technique',
}

export default function Annuaire() {
  const [assos, setAssos] = useState<AssociationVue[] | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  useEffect(() => {
    // AbortController : la requête est annulée si le composant est démonté.
    // La v1 n'en avait aucun et appelait setState après démontage.
    const ctrl = new AbortController()
    api
      .annuaire()
      .then((r) => { if (!ctrl.signal.aborted) setAssos(r) })
      .catch((e: unknown) => {
        if (ctrl.signal.aborted) return
        setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible')
      })
    return () => ctrl.abort()
  }, [])

  if (erreur) {
    return (
      <main className="page page--centree">
        <h1>Associations de l'ENSIM</h1>
        <p role="alert" className="erreur">{erreur}</p>
      </main>
    )
  }

  if (!assos) {
    return (
      <main className="page page--centree">
        <h1>Associations de l'ENSIM</h1>
        <p aria-live="polite">Chargement…</p>
      </main>
    )
  }

  return (
    <main className="page page--centree">
      <header className="annuaire__entete">
        <h1>Associations de l'ENSIM</h1>
        <p>{assos.length} associations, clubs et bureaux.</p>
      </header>

      <ul className="annuaire">
        {assos.map((a) => (
          <li key={a.slug}>
            <Link to={`/assos/${a.slug}`}>
              <span className="annuaire__nom">{a.nom}</span>
              <span className="annuaire__type">{LIBELLE_TYPE[a.type] ?? a.type}</span>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  )
}
