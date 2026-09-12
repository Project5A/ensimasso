import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ErreurApi } from '../api'
import type { PageRendue } from '../types'
import { RenduPage } from './RenduPage'

/**
 * Une page publique d'association.
 *
 * <p>Le même composant sert la page courante et l'archive : une année passée
 * est simplement un autre mandat. C'est la propriété la plus utile du modèle
 * temporel — l'archive ne coûte aucun code.
 */
export default function PageAsso({ archive = false }: { archive?: boolean }) {
  const { slug = '', annee = '', pageSlug = 'accueil' } = useParams()
  const [page, setPage] = useState<PageRendue | null>(null)
  const [erreur, setErreur] = useState<{ statut: number; message: string } | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setPage(null)
    setErreur(null)

    const promesse = archive ? api.archive(slug, annee, pageSlug) : api.page(slug, pageSlug)
    promesse
      .then((r) => { if (!ctrl.signal.aborted) setPage(r) })
      .catch((e: unknown) => {
        if (ctrl.signal.aborted) return
        setErreur(
          e instanceof ErreurApi
            ? { statut: e.statut, message: e.message }
            : { statut: 0, message: 'Service indisponible' },
        )
      })
    return () => ctrl.abort()
  }, [slug, annee, pageSlug, archive])

  if (erreur) {
    return (
      <main className="page page--centree">
        <h1>{erreur.statut === 404 ? 'Page introuvable' : 'Erreur'}</h1>
        <p role="alert" className="erreur">{erreur.message}</p>
        <p><Link to="/">Retour à l'annuaire</Link></p>
      </main>
    )
  }

  if (!page) {
    return (
      <main className="page page--centree">
        <p aria-live="polite">Chargement…</p>
      </main>
    )
  }

  return <RenduPage page={page} />
}
