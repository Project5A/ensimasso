import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  apiDashboard, ErreurApi,
  type BlocVue, type TypeBlocVue, type VersionVue,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { ChampsSchema } from './ChampsSchema'

/**
 * L'éditeur de page.
 *
 * <p>Éditer n'écrit jamais dans la version publiée : ouvrir l'éditeur ouvre
 * (ou récupère) un BROUILLON, cloné depuis la version en ligne. Publier crée
 * une nouvelle version et archive la précédente. Le site public ne change pas
 * tant que personne n'a cliqué sur « Publier ».
 *
 * <p>Ce n'est pas une convention d'interface : la base refuse toute écriture
 * sur les blocs d'une version publiée, et interdit la transition
 * PUBLIEE → BROUILLON. L'éditeur ne peut pas réécrire l'histoire même s'il
 * était bogué.
 */

/**
 * Le payload avec lequel un bloc est créé.
 *
 * La palette envoyait `{}` pour tous les types. Huit schémas sur onze
 * déclarent des propriétés requises : la bannière, le texte, le trombinoscope,
 * la galerie, la FAQ, les chiffres, l'intégration et le compte à rebours
 * étaient refusés en 422 et ne pouvaient pas être créés du tout. Vérifié en
 * passant le validateur réel sur le registre réel : 8 types sur 11.
 *
 * Le défaut vient du registre, à côté du schéma qu'il doit respecter — sinon
 * ajouter un type de bloc demanderait de penser à venir modifier ce fichier.
 */
function payloadDeDepart(t: TypeBlocVue): Record<string, unknown> {
  try {
    const p = JSON.parse(t.payloadDefaut) as unknown
    return p && typeof p === 'object' && !Array.isArray(p) ? (p as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}

export default function Editeur() {
  const { pageId = '' } = useParams()
  const { jeton } = useAuth()

  const [version, setVersion] = useState<VersionVue | null>(null)
  const [blocs, setBlocs] = useState<BlocVue[]>([])
  const [catalogue, setCatalogue] = useState<TypeBlocVue[]>([])
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [selection, setSelection] = useState<string | null>(null)
  const [occupe, setOccupe] = useState(false)
  // « La liste affichée est-elle celle de la base ? » — distincte de « la page
  // est-elle vide ». Les confondre faisait dire « Aucun bloc » d'une page qui
  // n'était pas vide.
  const [listeSure, setListeSure] = useState(true)

  const rafraichirBlocs = useCallback(async (versionId: string) => {
    setBlocs(await apiDashboard.blocs(jeton(), versionId))
    setListeSure(true)
  }, [jeton])

  // Le jeton est lu à l'appel, à travers une référence, et l'amorçage ne dépend
  // QUE de la page.
  //
  // `jeton` change d'identité à chaque renouvellement silencieux du jeton OIDC
  // — `automaticSilentRenew` est actif et `addUserLoaded` remplace l'utilisateur
  // toutes les quelques minutes. Tant qu'il figurait dans les dépendances,
  // l'amorçage se rejouait donc tout seul, et `ouvrirBrouillon` est une
  // ÉCRITURE : sur une page qu'on venait de publier, il créait une version que
  // personne n'avait demandée, et l'écran retombait de « Version N publiée » à
  // « Brouillon — version N+1 » sans qu'on y ait touché.
  const refJeton = useRef(jeton)
  useEffect(() => { refJeton.current = jeton }, [jeton])

  useEffect(() => {
    let vivant = true
    async function amorcer() {
      const lire = refJeton.current
      try {
        const [v, c] = await Promise.all([
          apiDashboard.ouvrirBrouillon(lire(), pageId),
          apiDashboard.catalogue(lire()),
        ])
        if (!vivant) return
        setVersion(v)
        setCatalogue(c)
        setBlocs(await apiDashboard.blocs(lire(), v.id))
      } catch (e: unknown) {
        if (vivant) setErreur(e instanceof ErreurApi ? e.message : 'Ouverture impossible')
      }
    }
    void amorcer()
    return () => { vivant = false }
  }, [pageId])

  /**
   * Exécute une action, puis relit les blocs.
   *
   * <p>L'action peut rendre la version sur laquelle l'éditeur doit CONTINUER :
   * publier fige la version courante, reprendre l'édition en ouvre une neuve.
   * Sans ça, `agir` relisait toujours `version.id` — celui capturé au rendu —
   * et affichait donc les blocs de la version qu'on venait de quitter, dont les
   * identifiants appartiennent à une version figée. Chaque bouton d'une ligne
   * ainsi affichée visait une ligne intouchable : 409.
   */
  async function agir(action: () => Promise<VersionVue | void>, message?: string) {
    if (occupe || !version) return
    setOccupe(true); setErreur(null); setInfo(null)

    let suivante: VersionVue
    try {
      suivante = (await action()) ?? version
      setVersion(suivante)
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
      setOccupe(false)
      return
    }

    // À partir d'ici, l'action a ABOUTI. Ce qui suit n'est qu'un
    // rafraîchissement d'affichage, et son échec était raconté comme un échec
    // de l'action : publier une page puis perdre le réseau affichait
    // « Opération impossible » sur une page bel et bien publiée, sans le
    // moindre message de succès. On republiait — en 409.
    //
    // Pire, les lignes restées à l'écran étaient celles de la version
    // PRÉCÉDENTE : leurs identifiants appartenaient à une version figée, et
    // chaque bouton d'une de ces lignes repartait en 409. Elles sont donc
    // retirées, et l'écran dit les deux choses : ce qui a marché, et ce qu'il
    // n'a pas pu relire.
    try {
      await rafraichirBlocs(suivante.id)
      if (message) setInfo(message)
    } catch {
      // Vider n'a de sens QUE si la version a changé : les lignes à l'écran
      // appartiennent alors à celle qu'on vient de quitter, et chacun de leurs
      // boutons repartirait en 409. Sur la MÊME version — supprimer, modifier,
      // réordonner, ajouter — elles restent justes, seulement un peu en
      // retard ; les effacer faisait dire « Aucun bloc. Ajoutez-en un
      // ci-dessus. » d'une page qui n'était pas vide, palette active, et le
      // bloc ajouté se rangeait derrière ceux qu'on croyait disparus.
      if (suivante.id !== version.id) {
        setBlocs([])
      }
      setListeSure(false)
      setErreur('Action effectuée, mais la liste des blocs n’a pas pu être '
              + 'relue. Rechargez la page pour la revoir.')
    } finally {
      setOccupe(false)
    }
  }

  const deplacer = (index: number, delta: number) => {
    const cible = index + delta
    if (!version || cible < 0 || cible >= blocs.length) return
    const ordre = blocs.map((b) => b.id)
    const [a, b] = [ordre[index], ordre[cible]]
    if (!a || !b) return
    ordre[index] = b
    ordre[cible] = a
    void agir(() => apiDashboard.reordonner(jeton(), version.id, ordre))
  }

  if (erreur && !version) {
    return (
      <main className="page page--centree">
        <h1>Éditeur</h1>
        <p role="alert" className="erreur">{erreur}</p>
        <p><Link to="/tableau">Retour au tableau de bord</Link></p>
      </main>
    )
  }
  // Publier FIGE la version. L'éditeur gardait pourtant la même dans son état :
  // le bandeau continuait d'annoncer un brouillon, et toute action suivante —
  // ajouter un bloc, réordonner, republier — visait une version publiée, que le
  // domaine et le trigger bloc_fige refusent. On obtenait un 409 par clic, sans
  // que rien n'explique pourquoi.
  const estBrouillon = version?.statut === 'BROUILLON'

  if (!version) {
    return <main className="page page--centree"><p aria-live="polite">Ouverture du brouillon…</p></main>
  }

  return (
    <main className="page editeur">
      <nav className="fil"><Link to="/tableau">Tableau de bord</Link><span aria-hidden="true"> › </span><span>Éditeur</span></nav>

      <header className="editeur__entete">
        <div>
          <h1>
            {estBrouillon
              ? `Brouillon — version ${version.numero}`
              : `Version ${version.numero} publiée`}
          </h1>
          <p className="aide">
            {estBrouillon
              ? "Le site public ne change pas tant que vous n'avez pas publié."
              : 'Cette version est figée. Reprendre l’édition ouvre un nouveau brouillon, copié depuis celle-ci.'}
          </p>
        </div>
        <div className="editeur__actions">
        {/* L'aperçu avant la publication, et dans cet ordre : c'est celui dans
            lequel on veut qu'ils soient utilisés. */}
        <Link className="bouton bouton--secondaire" to={`/tableau/apercu/${version.id}`}>
          Aperçu
        </Link>
        {estBrouillon ? (
          <button
            className="bouton"
            disabled={occupe || blocs.length === 0}
            onClick={() =>
              void agir(
                () => apiDashboard.publier(jeton(), version.id),
                'Page publiée. Elle est désormais visible du public.',
              )
            }
          >
            Publier
          </button>
        ) : (
          // Reprendre l'édition n'est pas « revenir en arrière » : c'est ouvrir
          // un NOUVEAU brouillon, copié depuis la version publiée. Le service
          // est idempotent — s'il en existe déjà un, il le rend.
          <button
            className="bouton"
            disabled={occupe}
            onClick={() =>
              void agir(
                () => apiDashboard.ouvrirBrouillon(jeton(), pageId),
                'Nouveau brouillon ouvert.',
              )
            }
          >
            Reprendre l’édition
          </button>
        )}
        </div>
      </header>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}

      <section className="editeur__ajout">
        <h2>Ajouter un bloc</h2>
        <div className="palette">
          {catalogue.map((t) => (
            <button key={`${t.type}-${t.schemaVersion}`} type="button" className="palette__item"
                    // Ajouter à une liste qu'on n'a pas pu relire, c'est
                    // ajouter à l'aveugle : le serveur range le bloc derrière
                    // ceux qui sont toujours là et que l'écran ne montre plus.
                    disabled={occupe || !estBrouillon || !listeSure}
                    onClick={() =>
                      void agir(async () => {
                        await apiDashboard.ajouterBloc(
                          jeton(), version.id, t.type, payloadDeDepart(t))
                      })
                    }>
              <span>{t.libelle}</span>
              <small>{t.categorie}</small>
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2>Contenu de la page</h2>
        {blocs.length === 0 && (
          listeSure
            ? <p className="vide">Aucun bloc. Ajoutez-en un ci-dessus.</p>
            // Deux choses différentes, et les dire pareil serait mentir : on ne
            // sait pas ce que contient cette page, on sait seulement qu'on n'a
            // pas pu le relire.
            : <p className="vide">Contenu inconnu : la liste n’a pas pu être relue.</p>
        )}

        <ol className="blocs">
          {blocs.map((bloc, i) => {
            const type = catalogue.find(
              (t) => t.type === bloc.type && t.schemaVersion === bloc.schemaVersion,
            )
            const ouvert = selection === bloc.id
            return (
              <li key={bloc.id} className="bloc-edit">
                <div className="bloc-edit__barre">
                  <button type="button" className="lien" aria-expanded={ouvert}
                          onClick={() => setSelection(ouvert ? null : bloc.id)}>
                    {type?.libelle ?? bloc.type}
                  </button>
                  <div className="bloc-edit__actions">
                    <button type="button" aria-label="Monter"
                            disabled={i === 0 || occupe || !estBrouillon}
                            onClick={() => deplacer(i, -1)}>↑</button>
                    <button type="button" aria-label="Descendre"
                            disabled={i === blocs.length - 1 || occupe || !estBrouillon}
                            onClick={() => deplacer(i, +1)}>↓</button>
                    <button type="button" className="lien-danger" disabled={occupe || !estBrouillon}
                            onClick={() =>
                              void agir(() => apiDashboard.supprimerBloc(jeton(), bloc.id))}>
                      Supprimer
                    </button>
                  </div>
                </div>

                {ouvert && type && (
                  <FormulaireBloc
                    bloc={bloc}
                    type={type}
                    occupe={occupe}
                    // Le seul bouton d'écriture que le figeage de la version
                    // avait oublié : il restait actif sur une version publiée
                    // et repartait en 409, comme les autres avant lui.
                    modifiable={estBrouillon}
                    onEnregistrer={(payload) =>
                      agir(async () => {
                        await apiDashboard.modifierBloc(jeton(), bloc.id, payload)
                      }, 'Bloc enregistré.')
                    }
                  />
                )}

                {ouvert && !type && (
                  <p className="aide">
                    Ce bloc a été écrit avec une version de schéma que cette
                    interface ne connaît pas ({bloc.type}@{bloc.schemaVersion}).
                    Il reste affiché sur le site ; son édition demande une mise à jour.
                  </p>
                )}
              </li>
            )
          })}
        </ol>
      </section>
    </main>
  )
}

function FormulaireBloc({
  bloc, type, occupe, modifiable, onEnregistrer,
}: {
  bloc: BlocVue; type: TypeBlocVue; occupe: boolean; modifiable: boolean
  onEnregistrer: (payload: Record<string, unknown>) => Promise<void>
}) {
  const [valeur, setValeur] = useState<Record<string, unknown>>(() => {
    try { return JSON.parse(bloc.payload) as Record<string, unknown> } catch { return {} }
  })
  const schema = (() => {
    try { return JSON.parse(type.jsonSchema) } catch { return { properties: {} } }
  })()

  return (
    <form
      className="bloc-edit__form"
      onSubmit={(e) => { e.preventDefault(); void onEnregistrer(valeur) }}
    >
      {/* Le formulaire est généré depuis le MÊME schéma que celui qui valide
          côté serveur : il ne peut pas diverger de ce que la base accepte. */}
      <ChampsSchema schema={schema} valeur={valeur} onChange={setValeur} />
      {!modifiable && (
        <p className="aide">
          Cette version est publiée, donc figée. Reprenez l’édition en haut de
          la page pour modifier ce bloc.
        </p>
      )}
      <button className="bouton bouton--petit" type="submit"
              disabled={occupe || !modifiable}>
        Enregistrer
      </button>
    </form>
  )
}
