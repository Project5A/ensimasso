import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi } from '../api'
import { useAuth } from '../auth/AuthContext'
import { RenduPage } from '../pages/RenduPage'
import type { PageRendue } from '../types'

/**
 * L'aperçu d'un brouillon.
 *
 * <p>Rend la page avec {@link RenduPage}, c'est-à-dire le composant qui sert
 * aussi le site public. Ce n'est pas une économie de code : c'est la seule
 * façon qu'un aperçu dise la vérité. Un rendu parallèle finit par diverger, et
 * il diverge en silence.
 */
export default function Apercu() {
  const { versionId = '' } = useParams()
  const { jeton } = useAuth()
  const navigate = useNavigate()
  const [page, setPage] = useState<PageRendue | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    apiDashboard
      .apercu(jeton(), versionId)
      .then((p) => { if (!ctrl.signal.aborted) setPage(p) })
      .catch((e: unknown) => {
        if (!ctrl.signal.aborted) {
          setErreur(e instanceof ErreurApi ? e.message : 'Aperçu indisponible')
        }
      })
    return () => ctrl.abort()
  }, [versionId, jeton])

  if (erreur) {
    return (
      <main className="page page--centree">
        <h1>Aperçu indisponible</h1>
        <p role="alert" className="erreur">{erreur}</p>
        <p><Link to="/tableau">Retour au tableau de bord</Link></p>
      </main>
    )
  }
  if (!page) {
    return <main className="page page--centree"><p aria-live="polite">Chargement de l'aperçu…</p></main>
  }

  return (
    <>
      {/* Barre permanente et non masquable : confondre un aperçu avec le site
          en ligne conduit à croire qu'on a publié alors qu'on n'a rien publié. */}
      <div className="bandeau-apercu" role="note">
        <span>
          <strong>Aperçu du brouillon</strong> — version {page.versionNumero}, non publiée.
          Le site public n'a pas changé.
        </span>
        <button className="lien" type="button" onClick={() => navigate(-1)}>
          Retour à l'éditeur
        </button>
      </div>
      <RenduPage page={page} apercu />
    </>
  )
}
