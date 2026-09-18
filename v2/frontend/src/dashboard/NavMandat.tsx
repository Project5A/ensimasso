import { Link } from 'react-router-dom'

/**
 * La navigation d'un mandat : pages, agenda, partenaires, médiathèque, thème,
 * passation.
 *
 * <p>Tout ce que gère un bureau est rattaché à SON mandat, et l'URL le dit.
 * Un lien de tableau de bord porte donc toujours l'identifiant du mandat — il
 * n'existe pas d'écran « courant » dont le sens change tout seul en avril.
 */
export function NavMandat({ mandatId, actif }: { mandatId: string; actif: string }) {
  const onglets = [
    { cle: 'pages', libelle: 'Pages', vers: `/tableau/mandats/${mandatId}` },
    { cle: 'agenda', libelle: 'Agenda', vers: `/tableau/mandats/${mandatId}/agenda` },
    { cle: 'partenaires', libelle: 'Partenaires', vers: `/tableau/mandats/${mandatId}/partenaires` },
    { cle: 'medias', libelle: 'Médiathèque', vers: `/tableau/mandats/${mandatId}/medias` },
    { cle: 'theme', libelle: 'Thème', vers: `/tableau/mandats/${mandatId}/theme` },
    { cle: 'passation', libelle: 'Passation', vers: `/tableau/mandats/${mandatId}/passation` },
  ]
  return (
    <nav className="onglets" aria-label="Sections du mandat">
      {onglets.map((o) => (
        <Link key={o.cle} to={o.vers} aria-current={o.cle === actif ? 'page' : undefined}>
          {o.libelle}
        </Link>
      ))}
    </nav>
  )
}

/** Le fil d'Ariane commun aux écrans d'un mandat. */
export function FilMandat({ section }: { section: string }) {
  return (
    <nav className="fil">
      <Link to="/tableau">Tableau de bord</Link>
      <span aria-hidden="true"> › </span>
      <span>{section}</span>
    </nav>
  )
}
