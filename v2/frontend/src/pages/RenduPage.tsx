import { Link } from 'react-router-dom'
import { RendreBloc } from '../blocs/registre'
import { variablesDuTheme } from '../theme'
import type { PageRendue } from '../types'

/**
 * Le rendu d'une page, sans le chargement.
 *
 * <p>Séparé de {@link PageAsso} pour une seule raison : l'aperçu d'un brouillon
 * doit emprunter le MÊME composant que la page publique. Un aperçu qui passe
 * par un rendu parallèle finit toujours par diverger — et il diverge en
 * silence, ce qui est pire que de ne pas avoir d'aperçu du tout.
 *
 * <p>En mode aperçu, le menu et les liens d'archive ne sont pas cliquables :
 * chaque autre page a son propre brouillon, et ces liens mèneraient vers ce qui
 * est publié, c'est-à-dire vers autre chose que ce qu'on est en train de
 * relire.
 */
export function RenduPage({ page, apercu = false }: { page: PageRendue; apercu?: boolean }) {
  const autresAnnees = page.anneesDisponibles.filter((a) => a !== page.mandat.anneeCode)

  return (
    // Les jetons du thème du MANDAT deviennent des variables CSS : c'est ce qui
    // fait qu'une archive garde l'apparence de son année.
    <div className="asso" style={variablesDuTheme(page.theme)}>
      <header className="asso__entete">
        <nav className="asso__fil" aria-label="Fil d'Ariane">
          {apercu ? <span>Associations</span> : <Link to="/">Associations</Link>}
          <span aria-hidden="true"> › </span>
          <span>{page.association.nom}</span>
        </nav>

        <nav className="asso__menu" aria-label="Pages de l'association">
          {page.menu.map((p) =>
            apercu ? (
              <span key={p.slug} aria-current={p.slug === page.slug ? 'page' : undefined}>
                {p.titre}
              </span>
            ) : (
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
            ),
          )}
        </nav>
      </header>

      {!apercu && !page.mandat.estCourant && (
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
                  {apercu ? (
                    <span>{a}</span>
                  ) : (
                    <Link to={`/assos/${page.association.slug}/${a}/accueil`}>{a}</Link>
                  )}
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
