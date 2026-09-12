import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ErreurApi } from '../api'
import { RendreBloc } from '../blocs/registre'
import { variablesDuTheme } from '../theme'
import type { PageRendue } from '../types'

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

  const autresAnnees = page.anneesDisponibles.filter((a) => a !== page.mandat.anneeCode)

  return (
    // Les jetons du thème du MANDAT deviennent des variables CSS : c'est ce qui
    // fait qu'une archive garde l'apparence de son année.
    <div className="asso" style={variablesDuTheme(page.theme)}>
      <header className="asso__entete">
        <nav className="asso__fil" aria-label="Fil d'Ariane">
          <Link to="/">Associations</Link>
          <span aria-hidden="true"> › </span>
          <span>{page.association.nom}</span>
        </nav>

        <nav className="asso__menu" aria-label="Pages de l'association">
          {page.menu.map((p) => (
            <Link
              key={p.slug}
              to={
                page.mandat.estCourant
                  ? `/assos/${page.association.slug}/${p.slug}`
                  : `/assos/${page.association.slug}/${page.mandat.anneeCode}/${p.slug}`
              }
              aria-current={p.slug === page.slug ? 'page' : undefined}
            >
              {p.titre}
            </Link>
          ))}
        </nav>
      </header>

      {!page.mandat.estCourant && (
        <p className="bandeau-archive" role="note">
          Vous consultez l'archive <strong>{page.mandat.anneeCode}</strong>.{' '}
          <Link to={`/assos/${page.association.slug}`}>Voir la page actuelle</Link>
        </p>
      )}

      <main>
        {page.blocs.map((bloc) => (
          <RendreBloc
            key={bloc.id}
            bloc={bloc}
            slug={page.association.slug}
            anneeCode={page.mandat.anneeCode}
            estCourant={page.mandat.estCourant}
          />
        ))}
      </main>

      <footer className="asso__pied">
        {autresAnnees.length > 0 && (
          <nav aria-label="Années précédentes">
            <h2>Années précédentes</h2>
            <ul>
              {autresAnnees.map((a) => (
                <li key={a}>
                  <Link to={`/assos/${page.association.slug}/${a}/accueil`}>{a}</Link>
                </li>
              ))}
            </ul>
          </nav>
        )}
        <p className="asso__mention">
          {page.association.nom} — mandat {page.mandat.anneeCode}, version {page.versionNumero}
        </p>
      </footer>
    </div>
  )
}
