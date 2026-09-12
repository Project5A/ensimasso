import { useEffect, useState } from 'react'
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

/* ------------------------------------------------------------------ agenda */

const JOUR_MOIS = new Intl.DateTimeFormat('fr-FR', {
  weekday: 'short',
  day: 'numeric',
  month: 'long',
})
const JOUR_MOIS_AN = new Intl.DateTimeFormat('fr-FR', {
  weekday: 'short',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})
const HEURE = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' })

/** `null` plutôt qu'une exception : une date illisible ne doit pas tuer la page. */
function dateValide(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? null : d
}

/**
 * L'année est toujours écrite. Sur une page d'archive, « mar. 4 février » ne
 * dit pas de quelle année il s'agit, et c'est précisément l'information que
 * vient chercher quelqu'un qui consulte l'archive d'un mandat.
 */
export function periode(debut: Date, fin: Date | null): string {
  if (fin === null) return `${JOUR_MOIS_AN.format(debut)} à ${HEURE.format(debut)}`
  // Une soirée qui finit à 2 h du matin est une soirée, pas un évènement de
  // deux jours : « du sam. 3 au dim. 4 octobre » pour un gala décrirait mal ce
  // que vit celui qui y va. Le cas est la règle dans une association étudiante.
  const memeSoiree =
    fin.getTime() - debut.getTime() <= 12 * 3600 * 1000 && fin.getHours() < 6
  if (debut.toDateString() === fin.toDateString() || memeSoiree) {
    return `${JOUR_MOIS_AN.format(debut)}, ${HEURE.format(debut)} – ${HEURE.format(fin)}`
  }
  // Sur plusieurs jours d'une même année, l'année n'est écrite qu'une fois.
  const memeAnnee = debut.getFullYear() === fin.getFullYear()
  const d = memeAnnee ? JOUR_MOIS.format(debut) : JOUR_MOIS_AN.format(debut)
  return `du ${d} au ${JOUR_MOIS_AN.format(fin)}`
}

/**
 * L'agenda du mandat de la page.
 *
 * <p>Le serveur a déjà filtré et trié : ce composant n'applique aucune règle
 * temporelle, il affiche. C'est volontaire — la règle « sur une archive, tout
 * est passé, donc on montre le bilan » doit exister à un seul endroit, et elle
 * est testée là-bas.
 *
 * <p>Un évènement annulé est rendu, barré, avec son motif.
 */
export function EventList({
  bloc,
  anneeCode,
  estCourant,
}: {
  bloc: BlocRendu
  anneeCode: string
  estCourant: boolean
}) {
  const evenements = bloc.agenda ?? []
  const style = (texte(bloc.payload.style) ?? 'LISTE').toLowerCase()
  const titre = estCourant ? 'À venir' : `Les évènements de ${anneeCode}`

  if (evenements.length === 0) {
    return (
      <section className="agenda">
        <h2>{titre}</h2>
        <p className="vide">Aucun évènement annoncé pour le moment.</p>
      </section>
    )
  }

  return (
    <section className="agenda" aria-label={titre}>
      <h2>{titre}</h2>
      <ul className={`agenda__liste agenda__liste--${style}`}>
        {evenements.map((e) => {
          const debut = dateValide(e.debutLe)
          const annule = e.statut === 'ANNULE'
          return (
            <li key={e.slug} className={`evenement${annule ? ' evenement--annule' : ''}`}>
              {e.afficheUrl && (
                <img className="evenement__affiche" src={e.afficheUrl} alt="" loading="lazy" />
              )}
              <div className="evenement__corps">
                {debut && (
                  <time className="evenement__date" dateTime={e.debutLe}>
                    {periode(debut, dateValide(e.finLe))}
                  </time>
                )}
                <h3>{e.titre}</h3>
                {e.lieu && <p className="evenement__lieu">{e.lieu}</p>}
                {e.resume && <p className="evenement__resume">{e.resume}</p>}
                <p className="evenement__etats">
                  {annule && <span className="etiquette etiquette--annule">Annulé</span>}
                  {!annule && e.complet && (
                    <span className="etiquette etiquette--complet">Complet</span>
                  )}
                </p>
                {annule && e.motifAnnulation && (
                  <p className="evenement__motif">{e.motifAnnulation}</p>
                )}
                {/* Le lien de billetterie disparaît si l'évènement est annulé :
                    laisser vendre des places pour une soirée annulée serait pire
                    que de ne rien afficher. */}
                {!annule && e.lien && (
                  <a
                    className="bouton bouton--secondaire"
                    href={e.lien}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Billetterie
                  </a>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

/* -------------------------------------------------------------- partenaires */

const LIBELLE_NIVEAU: Record<string, string> = {
  OR: 'Partenaires principaux',
  ARGENT: 'Partenaires',
  BRONZE: 'Soutiens',
  SOUTIEN: 'Ils nous accompagnent',
}

/**
 * Les partenaires du mandat de la page.
 *
 * <p>Rattachés au mandat, donc à ce bureau-là : un partenaire de 2024-2025 ne
 * réapparaît pas de lui-même sur la page de 2026-2027 avec sa mention
 * « partenaire officiel », ce qu'aucun des deux signataires n'a demandé.
 */
export function Partners({ bloc }: { bloc: BlocRendu }) {
  const partenaires = bloc.partenaires ?? []
  const titre = texte(bloc.payload.titre) ?? 'Nos partenaires'
  if (partenaires.length === 0) return null

  const niveaux = ['OR', 'ARGENT', 'BRONZE', 'SOUTIEN'].filter((n) =>
    partenaires.some((p) => p.niveau === n),
  )

  return (
    <section className="partenaires" aria-label={titre}>
      <h2>{titre}</h2>
      {niveaux.map((niveau) => (
        <div key={niveau} className={`partenaires__niveau partenaires__niveau--${niveau.toLowerCase()}`}>
          <h3>{LIBELLE_NIVEAU[niveau]}</h3>
          <ul>
            {partenaires
              .filter((p) => p.niveau === niveau)
              .map((p) => {
                const contenu = p.logoUrl ? (
                  <img src={p.logoUrl} alt={p.nom} loading="lazy" />
                ) : (
                  <span>{p.nom}</span>
                )
                return (
                  <li key={p.nom}>
                    {p.url ? (
                      <a href={p.url} target="_blank" rel="noopener noreferrer sponsored">
                        {contenu}
                      </a>
                    ) : (
                      contenu
                    )}
                  </li>
                )
              })}
          </ul>
        </div>
      ))}
    </section>
  )
}

/* ------------------------------------------------------------ intégrations */

/**
 * Les seules intégrations autorisées, et la forme exacte de leur référence.
 *
 * <p>L'URL de l'iframe est FABRIQUÉE ici à partir d'une liste blanche : rien de
 * ce qui vient du payload ne devient une URL. Le serveur valide déjà
 * `fournisseur` contre une énumération et `ref` contre un motif, mais un bloc
 * rédigé sous un schéma plus ancien, ou une base restaurée d'un autre
 * environnement, passeraient sous ce contrôle. Une iframe dont l'URL vient du
 * contenu est un trou par lequel on sert ce qu'on veut depuis notre origine.
 */
const INTEGRATIONS: Record<
  string,
  { motif: RegExp; url: (ref: string) => string; titre: string; ratio: string }
> = {
  YOUTUBE: {
    motif: /^[A-Za-z0-9_-]{6,20}$/,
    // youtube-nocookie : pas de traceur posé avant que le visiteur ne lance la vidéo.
    url: (ref) => `https://www.youtube-nocookie.com/embed/${encodeURIComponent(ref)}`,
    titre: 'Vidéo YouTube',
    ratio: '16 / 9',
  },
  INSTAGRAM: {
    motif: /^[A-Za-z0-9_-]{5,30}$/,
    url: (ref) => `https://www.instagram.com/p/${encodeURIComponent(ref)}/embed`,
    titre: 'Publication Instagram',
    ratio: '4 / 5',
  },
  SPOTIFY: {
    motif: /^(track|album|playlist|episode):[A-Za-z0-9]{10,40}$/,
    url: (ref) => {
      const [type = 'track', id = ''] = ref.split(':')
      return `https://open.spotify.com/embed/${encodeURIComponent(type)}/${encodeURIComponent(id)}`
    },
    titre: 'Écouter sur Spotify',
    ratio: '16 / 9',
  },
}

export function Embed({ bloc }: { bloc: BlocRendu }) {
  const fournisseur = texte(bloc.payload.fournisseur) ?? ''
  const ref = texte(bloc.payload.ref) ?? ''
  const integration = INTEGRATIONS[fournisseur]

  // Fournisseur inconnu ou référence qui ne rentre pas dans le moule : on
  // n'affiche rien. Il n'existe pas de cas où deviner serait préférable.
  if (!integration || !integration.motif.test(ref)) {
    if (import.meta.env.DEV) {
      console.warn(`intégration refusée : ${fournisseur} / ${ref}`)
    }
    return null
  }

  return (
    <section className="integration">
      <div className="integration__cadre" style={{ aspectRatio: integration.ratio }}>
        <iframe
          src={integration.url(ref)}
          title={integration.titre}
          loading="lazy"
          referrerPolicy="strict-origin-when-cross-origin"
          allow="accelerometer; clipboard-write; encrypted-media; picture-in-picture"
          allowFullScreen
        />
      </div>
    </section>
  )
}

/* ------------------------------------------------------- compte à rebours */

type Restant = { jours: number; heures: number; minutes: number; secondes: number }

export function resteJusqua(cible: Date, maintenant: Date): Restant | null {
  const delta = cible.getTime() - maintenant.getTime()
  if (delta <= 0) return null
  const secondes = Math.floor(delta / 1000)
  return {
    jours: Math.floor(secondes / 86400),
    heures: Math.floor((secondes % 86400) / 3600),
    minutes: Math.floor((secondes % 3600) / 60),
    secondes: secondes % 60,
  }
}

/**
 * Le compte à rebours.
 *
 * <p>Sa cible est dans son payload, pas une référence à un évènement : un
 * compte à rebours qui pointe vers un évènement supprimé devrait afficher quoi ?
 * La question n'a pas de bonne réponse, donc le modèle ne la pose pas.
 *
 * <p>Une fois la date passée, le bloc affiche `messageApres` — sans lui il
 * afficherait « 0 jour » indéfiniment, ce qui est la façon la plus sûre de
 * faire croire qu'un site est à l'abandon.
 */
export function Countdown({ bloc }: { bloc: BlocRendu }) {
  const titre = texte(bloc.payload.titre) ?? ''
  const sousTitre = texte(bloc.payload.sousTitre)
  const messageApres = texte(bloc.payload.messageApres)
  const action = bloc.payload.action as { libelle?: string; href?: string } | undefined
  const cible = dateValide(texte(bloc.payload.cibleLe))

  const [restant, setRestant] = useState<Restant | null>(() =>
    cible ? resteJusqua(cible, new Date()) : null,
  )

  useEffect(() => {
    if (!cible) return
    const t = setInterval(() => setRestant(resteJusqua(cible, new Date())), 1000)
    return () => clearInterval(t)
  }, [cible?.getTime()])

  if (!cible) return null

  if (restant === null) {
    return messageApres ? (
      <section className="rebours rebours--passe">
        <h2>{titre}</h2>
        <p>{messageApres}</p>
      </section>
    ) : null
  }

  const unites: [number, string][] = [
    [restant.jours, restant.jours > 1 ? 'jours' : 'jour'],
    [restant.heures, 'h'],
    [restant.minutes, 'min'],
    [restant.secondes, 's'],
  ]

  return (
    <section className="rebours">
      <h2>{titre}</h2>
      {sousTitre && <p className="rebours__sous-titre">{sousTitre}</p>}
      {/* aria-hidden sur le cadran, qui changerait toutes les secondes, et une
          formulation lisible une seule fois pour les lecteurs d'écran. */}
      <p className="sr-seulement">
        Dans {restant.jours} jours, {restant.heures} heures et {restant.minutes} minutes.
      </p>
      <ol className="rebours__cadran" aria-hidden="true">
        {unites.map(([valeur, libelle]) => (
          <li key={libelle}>
            <span className="rebours__valeur">{String(valeur).padStart(2, '0')}</span>
            <span className="rebours__unite">{libelle}</span>
          </li>
        ))}
      </ol>
      {action?.libelle && action.href && (
        <a className="bouton" href={action.href}>
          {action.libelle}
        </a>
      )}
    </section>
  )
}
