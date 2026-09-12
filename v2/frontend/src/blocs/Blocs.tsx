import type { BlocRendu, MembreVue } from '../types'

/* Chaque composant lit SON payload avec prudence : le contenu vient de JSONB
   validé par JSON Schema côté serveur, mais un bloc écrit sous un schéma plus
   ancien peut manquer un champ. Rien ne doit faire tomber la page. */

const texte = (v: unknown): string | null => (typeof v === 'string' && v.trim() ? v : null)
const liste = (v: unknown): unknown[] => (Array.isArray(v) ? v : [])

export function Hero({ bloc }: { bloc: BlocRendu }) {
  const titre = texte(bloc.payload.titre) ?? ''
  const sousTitre = texte(bloc.payload.sousTitre)
  const cle = texte(bloc.payload.mediaKey)
  const image = cle ? bloc.urlsMedias[cle] : undefined
  const hauteur = texte(bloc.payload.hauteur) ?? 'MOYENNE'
  const actions = liste(bloc.payload.actions) as { libelle?: string; href?: string; style?: string }[]

  return (
    <section className={`hero hero--${hauteur.toLowerCase()}`}>
      {image && <img className="hero__fond" src={image} alt="" aria-hidden="true" />}
      <div className="hero__contenu">
        <h1>{titre}</h1>
        {sousTitre && <p className="hero__sous-titre">{sousTitre}</p>}
        {actions.length > 0 && (
          <div className="hero__actions">
            {actions.map((a, i) =>
              a.libelle && a.href ? (
                <a
                  key={i}
                  href={a.href}
                  className={a.style === 'SECONDAIRE' ? 'bouton bouton--secondaire' : 'bouton'}
                >
                  {a.libelle}
                </a>
              ) : null,
            )}
          </div>
        )}
      </div>
    </section>
  )
}

/**
 * Le texte riche est un DOCUMENT STRUCTURÉ, jamais une chaîne HTML.
 *
 * <p>Il n'y a donc aucun `dangerouslySetInnerHTML` ici, et aucun assainisseur
 * à maintenir : il n'existe pas de balisage à injecter. JSON Schema ne sait pas
 * exprimer « cette chaîne est du HTML sûr » — on supprime le problème plutôt
 * que de le filtrer. Un bloc écrit par un membre de n'importe laquelle des dix
 * associations s'affiche sur une page publique de notre origine.
 */
export function RichText({ bloc }: { bloc: BlocRendu }) {
  const doc = bloc.payload.doc as { content?: unknown[] } | undefined
  const largeur = texte(bloc.payload.largeur) ?? 'NORMALE'
  const paragraphes = liste(doc?.content)
    .map((n) => {
      const noeud = n as { type?: string; content?: { text?: string }[] }
      if (noeud.type !== 'paragraph') return null
      return liste(noeud.content)
        .map((f) => (f as { text?: string }).text ?? '')
        .join('')
    })
    .filter((p): p is string => !!p && p.trim().length > 0)

  if (paragraphes.length === 0) return null

  return (
    <section className={`prose prose--${largeur.toLowerCase()}`}>
      {paragraphes.map((p, i) => (
        <p key={i}>{p}</p>
      ))}
    </section>
  )
}

export function Stats({ bloc }: { bloc: BlocRendu }) {
  const items = liste(bloc.payload.items) as { libelle?: string; valeur?: string }[]
  if (items.length === 0) return null

  return (
    <section className="stats" aria-label="Chiffres clés">
      <dl>
        {items.map((it, i) => (
          <div className="stat" key={i}>
            <dd>{it.valeur}</dd>
            <dt>{it.libelle}</dt>
          </div>
        ))}
      </dl>
    </section>
  )
}

/**
 * Le trombinoscope.
 *
 * <p>L'équipe est résolue par le serveur contre le mandat DE CETTE PAGE, jamais
 * contre le mandat courant : la page 2023-2024 montre le bureau de 2023-2024
 * pour toujours. C'est le point que la revue d'architecture avait signalé comme
 * le plus facile à rater — et impossible à détecter en développement, où
 * « courant » et « le mandat de la page » sont la même chose.
 */
export function TeamGrid({ bloc, anneeCode }: { bloc: BlocRendu; anneeCode: string }) {
  const colonnes = typeof bloc.payload.colonnes === 'number' ? bloc.payload.colonnes : 3
  const equipe: MembreVue[] = bloc.equipe ?? []

  if (equipe.length === 0) {
    return (
      <section className="equipe">
        <p className="vide">Le bureau {anneeCode} n'est pas encore renseigné.</p>
      </section>
    )
  }

  return (
    <section className="equipe" aria-label={`Bureau ${anneeCode}`}>
      <h2>Le bureau {anneeCode}</h2>
      <ul className="equipe__grille" style={{ '--colonnes': colonnes } as React.CSSProperties}>
        {equipe.map((m, i) => (
          <li key={`${m.poste}-${i}`} className="membre">
            {m.photoUrl ? (
              <img src={m.photoUrl} alt={`Portrait — ${m.titreAffiche}`} loading="lazy" />
            ) : (
              <div className="membre__initiale" aria-hidden="true">
                {m.titreAffiche.charAt(0)}
              </div>
            )}
            <p className="membre__poste">{m.titreAffiche}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function Faq({ bloc }: { bloc: BlocRendu }) {
  const items = liste(bloc.payload.items) as { question?: string; reponse?: string }[]
  if (items.length === 0) return null

  return (
    <section className="faq">
      <h2>Questions fréquentes</h2>
      {items.map((it, i) =>
        it.question && it.reponse ? (
          // <details> : ouverture au clavier et repli natifs, sans JavaScript.
          <details key={i}>
            <summary>{it.question}</summary>
            <p>{it.reponse}</p>
          </details>
        ) : null,
      )}
    </section>
  )
}

export function CtaAdhesion({ bloc, slug }: { bloc: BlocRendu; slug: string }) {
  const titre = texte(bloc.payload.titre) ?? 'Adhérer'
  const texteCta = texte(bloc.payload.texte)
  const note = texte(bloc.payload.note)

  return (
    <section className="cta">
      <h2>{titre}</h2>
      {texteCta && <p>{texteCta}</p>}
      <a className="bouton" href={`/assos/${slug}/adherer`}>
        Adhérer
      </a>
      {/* Aucun prix ici : il vient des tarifs, côté serveur. */}
      {note && <p className="cta__note">{note}</p>}
    </section>
  )
}

export function Gallery({ bloc }: { bloc: BlocRendu }) {
  const cles = liste(bloc.payload.mediaKeys).filter((c): c is string => typeof c === 'string')
  const urls = cles.map((c) => bloc.urlsMedias[c]).filter((u): u is string => !!u)
  if (urls.length === 0) return null

  return (
    <section className="galerie" aria-label="Galerie">
      <ul>
        {urls.map((u, i) => (
          <li key={i}>
            <img src={u} alt="" loading="lazy" />
          </li>
        ))}
      </ul>
    </section>
  )
}

/**
 * Le repli pour un type de bloc que ce bundle ne connaît pas.
 *
 * <p>Le registre des types vit en base, le moteur de rendu est un bundle
 * compilé : insérer une ligne dans `type_bloc`, ou revenir en arrière sur le
 * front, produit nécessairement un type inconnu. La revue d'architecture avait
 * relevé que deux des trois conceptions proposées présentaient ce catalogue en
 * base comme un pur avantage, sans dire ce qui se passe alors. Ici : on ignore
 * proprement, on le signale en console, et la page reste debout.
 */
export function BlocInconnu({ bloc }: { bloc: BlocRendu }) {
  if (import.meta.env.DEV) {
    console.warn(`bloc non pris en charge par ce bundle : ${bloc.type}@${bloc.schemaVersion}`)
  }
  return null
}
