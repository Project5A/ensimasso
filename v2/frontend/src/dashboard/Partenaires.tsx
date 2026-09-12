import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiDashboard,
  ErreurApi,
  type PartenaireDashboard,
  type RedactionPartenaire,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

const NIVEAUX: { valeur: PartenaireDashboard['niveau']; libelle: string }[] = [
  { valeur: 'OR', libelle: 'Or — partenaire principal' },
  { valeur: 'ARGENT', libelle: 'Argent' },
  { valeur: 'BRONZE', libelle: 'Bronze' },
  { valeur: 'SOUTIEN', libelle: 'Soutien' },
]

type Brouillon = {
  nom: string
  niveau: PartenaireDashboard['niveau']
  url: string
  ordre: string
  visible: boolean
}

const VIDE: Brouillon = { nom: '', niveau: 'SOUTIEN', url: '', ordre: '0', visible: true }

function versRedaction(b: Brouillon): RedactionPartenaire | null {
  if (!b.nom.trim()) return null
  const ordre = Number.parseInt(b.ordre, 10)
  return {
    nom: b.nom.trim(),
    niveau: b.niveau,
    logoMediaKey: null,
    url: b.url.trim() || null,
    ordre: Number.isFinite(ordre) ? ordre : 0,
    visible: b.visible,
  }
}

/**
 * Les partenaires d'un mandat.
 *
 * <p>L'écran dit explicitement que la liste appartient à CE mandat. C'est la
 * question que pose tout nouveau bureau — « pourquoi la liste est-elle vide, on
 * avait des partenaires » — et la réponse est que les conventions se
 * renégocient : celles de l'an dernier ne l'engagent pas, et restent sur la
 * page d'archive où elles étaient vraies.
 */
export default function Partenaires() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const [partenaires, setPartenaires] = useState<PartenaireDashboard[] | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const [brouillon, setBrouillon] = useState<Brouillon>(VIDE)
  const [edite, setEdite] = useState<PartenaireDashboard | null>(null)
  const [enCours, setEnCours] = useState(false)

  const charger = useCallback(() => {
    apiDashboard
      .partenaires(jeton(), mandatId)
      .then(setPartenaires)
      .catch((e: unknown) => setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [jeton, mandatId])

  useEffect(() => { charger() }, [charger])

  function editer(p: PartenaireDashboard) {
    setEdite(p)
    setErreur(null)
    setBrouillon({
      nom: p.nom, niveau: p.niveau, url: p.url ?? '',
      ordre: String(p.ordre), visible: p.visible,
    })
  }

  function abandonner() {
    setEdite(null)
    setBrouillon(VIDE)
  }

  async function agir(action: () => Promise<unknown>) {
    if (enCours) return
    setEnCours(true)
    setErreur(null)
    try {
      await action()
      charger()
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
    } finally {
      setEnCours(false)
    }
  }

  async function soumettre(e: React.FormEvent) {
    e.preventDefault()
    const corps = versRedaction(brouillon)
    if (!corps) {
      setErreur('Le nom du partenaire est obligatoire.')
      return
    }
    await agir(async () => {
      if (edite) await apiDashboard.modifierPartenaire(jeton(), edite.id, corps)
      else await apiDashboard.creerPartenaire(jeton(), mandatId, corps)
      abandonner()
    })
  }

  return (
    <main className="page page--centree">
      <FilMandat section="Partenaires" />
      <h1>Partenaires du mandat</h1>
      <NavMandat mandatId={mandatId} actif="partenaires" />

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!partenaires && !erreur && <p aria-live="polite">Chargement…</p>}

      {partenaires && partenaires.length === 0 && (
        <div className="vide-etat">
          <h2>Aucun partenaire</h2>
          <p>
            La liste est propre à ce mandat : les conventions de l'an dernier
            n'engagent pas ce bureau, et restent affichées sur la page d'archive
            de l'année où elles étaient vraies.
          </p>
        </div>
      )}

      {partenaires && partenaires.length > 0 && (
        <ul className="liste-partenaires">
          {partenaires.map((p) => (
            <li key={p.id} className={p.visible ? undefined : 'partenaire--masque'}>
              <div>
                <p className="partenaire__nom">
                  {p.nom}
                  <span className={`etiquette etiquette--niveau-${p.niveau.toLowerCase()}`}>
                    {p.niveau}
                  </span>
                  {!p.visible && <span className="etiquette">Masqué</span>}
                </p>
                {p.url && <p className="aide">{p.url}</p>}
              </div>
              <div className="evenement-ligne__actions">
                <button className="lien" type="button" onClick={() => editer(p)}>Modifier</button>
                <button className="lien lien-danger" type="button" disabled={enCours}
                        onClick={() => void agir(() => apiDashboard.supprimerPartenaire(jeton(), p.id))}>
                  Retirer
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={soumettre} className="formulaire">
        <h2>{edite ? `Modifier « ${edite.nom} »` : 'Nouveau partenaire'}</h2>

        <label htmlFor="pa-nom">Nom</label>
        <input id="pa-nom" type="text" maxLength={120} required value={brouillon.nom}
               onChange={(e) => setBrouillon({ ...brouillon, nom: e.target.value })} />

        <div className="formulaire__paire">
          <div>
            <label htmlFor="pa-niveau">Niveau</label>
            <select id="pa-niveau" value={brouillon.niveau}
                    onChange={(e) => setBrouillon({
                      ...brouillon, niveau: e.target.value as PartenaireDashboard['niveau'],
                    })}>
              {NIVEAUX.map((n) => <option key={n.valeur} value={n.valeur}>{n.libelle}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="pa-ordre">Rang dans son niveau</label>
            <input id="pa-ordre" type="number" min={0} max={999} value={brouillon.ordre}
                   onChange={(e) => setBrouillon({ ...brouillon, ordre: e.target.value })} />
          </div>
        </div>

        <label htmlFor="pa-url">Site (http/https)</label>
        <input id="pa-url" type="url" maxLength={512} value={brouillon.url} placeholder="https://…"
               onChange={(e) => setBrouillon({ ...brouillon, url: e.target.value })} />

        <label className="case">
          <input type="checkbox" checked={brouillon.visible}
                 onChange={(e) => setBrouillon({ ...brouillon, visible: e.target.checked })} />
          Afficher sur les pages publiques
        </label>

        <div className="formulaire__actions">
          <button className="bouton" type="submit" disabled={enCours}>
            {edite ? 'Enregistrer' : 'Ajouter'}
          </button>
          {edite && <button className="lien" type="button" onClick={abandonner}>Abandonner</button>}
        </div>
        <p className="aide">
          Un partenariat en cours de négociation se saisit en « masqué » : il est
          conservé ici sans apparaître sur le site.
        </p>
      </form>
    </main>
  )
}
