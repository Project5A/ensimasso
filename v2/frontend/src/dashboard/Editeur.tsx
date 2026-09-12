import { useCallback, useEffect, useState } from 'react'
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

  const rafraichirBlocs = useCallback(
    async (versionId: string) => setBlocs(await apiDashboard.blocs(jeton(), versionId)),
    [jeton],
  )

  useEffect(() => {
    let vivant = true
    async function amorcer() {
      try {
        const [v, c] = await Promise.all([
          apiDashboard.ouvrirBrouillon(jeton(), pageId),
          apiDashboard.catalogue(jeton()),
        ])
        if (!vivant) return
        setVersion(v)
        setCatalogue(c)
        setBlocs(await apiDashboard.blocs(jeton(), v.id))
      } catch (e: unknown) {
        if (vivant) setErreur(e instanceof ErreurApi ? e.message : 'Ouverture impossible')
      }
    }
    void amorcer()
    return () => { vivant = false }
  }, [pageId, jeton])

  async function agir(action: () => Promise<unknown>, message?: string) {
    if (occupe || !version) return
    setOccupe(true); setErreur(null); setInfo(null)
    try {
      await action()
      await rafraichirBlocs(version.id)
      if (message) setInfo(message)
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
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
  if (!version) {
    return <main className="page page--centree"><p aria-live="polite">Ouverture du brouillon…</p></main>
  }

  return (
    <main className="page editeur">
      <nav className="fil"><Link to="/tableau">Tableau de bord</Link><span aria-hidden="true"> › </span><span>Éditeur</span></nav>

      <header className="editeur__entete">
        <div>
          <h1>Brouillon — version {version.numero}</h1>
          <p className="aide">
            Le site public ne change pas tant que vous n'avez pas publié.
          </p>
        </div>
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
      </header>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}

      <section className="editeur__ajout">
        <h2>Ajouter un bloc</h2>
        <div className="palette">
          {catalogue.map((t) => (
            <button key={`${t.type}-${t.schemaVersion}`} type="button" className="palette__item"
                    disabled={occupe}
                    onClick={() => void agir(() => apiDashboard.ajouterBloc(jeton(), version.id, t.type, {}))}>
              <span>{t.libelle}</span>
              <small>{t.categorie}</small>
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2>Contenu de la page</h2>
        {blocs.length === 0 && <p className="vide">Aucun bloc. Ajoutez-en un ci-dessus.</p>}

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
                    <button type="button" aria-label="Monter" disabled={i === 0 || occupe}
                            onClick={() => deplacer(i, -1)}>↑</button>
                    <button type="button" aria-label="Descendre" disabled={i === blocs.length - 1 || occupe}
                            onClick={() => deplacer(i, +1)}>↓</button>
                    <button type="button" className="lien-danger" disabled={occupe}
                            onClick={() => void agir(() => apiDashboard.supprimerBloc(jeton(), bloc.id))}>
                      Supprimer
                    </button>
                  </div>
                </div>

                {ouvert && type && (
                  <FormulaireBloc
                    bloc={bloc}
                    type={type}
                    occupe={occupe}
                    onEnregistrer={(payload) =>
                      agir(() => apiDashboard.modifierBloc(jeton(), bloc.id, payload), 'Bloc enregistré.')
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
  bloc, type, occupe, onEnregistrer,
}: {
  bloc: BlocVue; type: TypeBlocVue; occupe: boolean
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
      <button className="bouton bouton--petit" type="submit" disabled={occupe}>
        Enregistrer
      </button>
    </form>
  )
}
