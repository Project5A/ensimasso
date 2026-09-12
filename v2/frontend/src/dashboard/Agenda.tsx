import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiDashboard,
  ErreurApi,
  type EvenementDashboard,
  type RedactionEvenement,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { champVersIso, isoVersChamp } from './dates'
import { FilMandat, NavMandat } from './NavMandat'

const LIBELLE_STATUT: Record<string, string> = {
  BROUILLON: 'Brouillon',
  PUBLIE: 'Publié',
  ANNULE: 'Annulé',
}

type Brouillon = {
  titre: string
  lieu: string
  debut: string
  fin: string
  resume: string
  lien: string
  complet: boolean
}

const VIDE: Brouillon = { titre: '', lieu: '', debut: '', fin: '', resume: '', lien: '', complet: false }

function versRedaction(b: Brouillon): RedactionEvenement | null {
  const debutLe = champVersIso(b.debut)
  if (!b.titre.trim() || !debutLe) return null
  return {
    titre: b.titre.trim(),
    resume: b.resume.trim() || null,
    description: null,
    lieu: b.lieu.trim() || null,
    debutLe,
    finLe: champVersIso(b.fin),
    mediaKey: null,
    lien: b.lien.trim() || null,
    complet: b.complet,
  }
}

function slugDe(titre: string): string {
  return titre.trim().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
    .slice(0, 60)
}

/**
 * L'agenda d'un mandat.
 *
 * <p>Les actions disponibles suivent le cycle de vie du domaine, elles ne le
 * doublent pas : un évènement publié ne se supprime pas, il s'annule — et il
 * reste alors sur la page publique, barré, avec son motif. C'est le serveur qui
 * refuse, l'écran se contente de ne pas proposer un bouton qui échouerait.
 */
export default function Agenda() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const [evenements, setEvenements] = useState<EvenementDashboard[] | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const [brouillon, setBrouillon] = useState<Brouillon>(VIDE)
  const [edite, setEdite] = useState<EvenementDashboard | null>(null)
  // L'annulation se saisit en ligne, sous l'évènement concerné : le motif part
  // sur la page publique, il mérite un champ qu'on relit, pas une boîte de
  // dialogue du navigateur qu'on remplit à l'aveugle.
  const [annulation, setAnnulation] = useState<{ id: string; motif: string } | null>(null)
  const [enCours, setEnCours] = useState(false)

  const charger = useCallback(() => {
    apiDashboard
      .evenements(jeton(), mandatId)
      .then(setEvenements)
      .catch((e: unknown) => setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [jeton, mandatId])

  useEffect(() => { charger() }, [charger])

  function editer(ev: EvenementDashboard) {
    setEdite(ev)
    setErreur(null)
    setBrouillon({
      titre: ev.titre,
      lieu: ev.lieu ?? '',
      debut: isoVersChamp(ev.debutLe),
      fin: isoVersChamp(ev.finLe),
      resume: ev.resume ?? '',
      lien: ev.lien ?? '',
      complet: ev.complet,
    })
  }

  function annulerEdition() {
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
      setErreur('Un titre et une date de début sont nécessaires.')
      return
    }
    await agir(async () => {
      if (edite) {
        await apiDashboard.modifierEvenement(jeton(), edite.id, corps)
      } else {
        // Le slug est dérivé du titre ; le serveur le valide avec le même motif.
        await apiDashboard.creerEvenement(jeton(), mandatId, { ...corps, slug: slugDe(corps.titre) })
      }
      annulerEdition()
    })
  }

  async function confirmerAnnulation(e: React.FormEvent) {
    e.preventDefault()
    if (!annulation) return
    const motif = annulation.motif.trim()
    await agir(async () => {
      await apiDashboard.annulerEvenement(jeton(), annulation.id, motif)
      setAnnulation(null)
    })
  }

  return (
    <main className="page page--centree">
      <FilMandat section="Agenda" />
      <h1>Agenda du mandat</h1>
      <NavMandat mandatId={mandatId} actif="agenda" />

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!evenements && !erreur && <p aria-live="polite">Chargement…</p>}

      {evenements && evenements.length === 0 && (
        <div className="vide-etat">
          <h2>Aucun évènement</h2>
          <p>
            Ce que vous créez ici alimente les blocs « Agenda » de vos pages, et
            reste attaché à ce mandat : le bureau suivant démarre avec un agenda
            vierge, et celui-ci ne disparaît pas pour autant.
          </p>
        </div>
      )}

      {evenements && evenements.length > 0 && (
        <ul className="liste-evenements">
          {evenements.map((ev) => (
            <li key={ev.id} className={`evenement-ligne evenement-ligne--${ev.statut.toLowerCase()}`}>
              <div className="evenement-ligne__texte">
                <p className="evenement-ligne__titre">
                  {ev.titre}
                  <span className={`etiquette etiquette--${ev.statut.toLowerCase()}`}>
                    {LIBELLE_STATUT[ev.statut] ?? ev.statut}
                  </span>
                  {ev.complet && ev.statut !== 'ANNULE' && (
                    <span className="etiquette etiquette--complet">Complet</span>
                  )}
                </p>
                <p className="aide">
                  <time dateTime={ev.debutLe}>{isoVersChamp(ev.debutLe).replace('T', ' à ')}</time>
                  {ev.lieu && <> — {ev.lieu}</>}
                </p>
                {ev.motifAnnulation && <p className="aide">Motif : {ev.motifAnnulation}</p>}

                {annulation?.id === ev.id && (
                  <form className="annulation" onSubmit={confirmerAnnulation}>
                    <label htmlFor={`motif-${ev.id}`}>
                      Motif d'annulation — il s'affichera sur la page publique
                    </label>
                    <div>
                      <input id={`motif-${ev.id}`} type="text" maxLength={400} required
                             value={annulation.motif} autoFocus
                             placeholder="Salle indisponible"
                             onChange={(e) => setAnnulation({ id: ev.id, motif: e.target.value })} />
                      <button className="bouton bouton--petit" type="submit" disabled={enCours}>
                        Confirmer l'annulation
                      </button>
                      <button className="lien" type="button" onClick={() => setAnnulation(null)}>
                        Renoncer
                      </button>
                    </div>
                    <p className="aide">
                      L'évènement restera visible, barré, et son lien de
                      billetterie disparaîtra.
                    </p>
                  </form>
                )}
              </div>

              <div className="evenement-ligne__actions">
                <button className="lien" type="button" onClick={() => editer(ev)}>Modifier</button>

                {ev.statut === 'BROUILLON' && (
                  <button className="lien" type="button" disabled={enCours}
                          onClick={() => void agir(() => apiDashboard.publierEvenement(jeton(), ev.id))}>
                    Publier
                  </button>
                )}

                {ev.statut === 'PUBLIE' && annulation?.id !== ev.id && (
                  <button className="lien" type="button" disabled={enCours}
                          onClick={() => setAnnulation({ id: ev.id, motif: '' })}>
                    Annuler
                  </button>
                )}

                {/* Supprimer n'existe que pour un brouillon : un évènement déjà
                    annoncé s'annule, il ne s'efface pas. */}
                {ev.statut === 'BROUILLON' && (
                  <button className="lien lien-danger" type="button" disabled={enCours}
                          onClick={() => void agir(() => apiDashboard.supprimerEvenement(jeton(), ev.id))}>
                    Supprimer
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={soumettre} className="formulaire">
        <h2>{edite ? `Modifier « ${edite.titre} »` : 'Nouvel évènement'}</h2>

        <label htmlFor="ev-titre">Titre</label>
        <input id="ev-titre" type="text" maxLength={160} required value={brouillon.titre}
               onChange={(e) => setBrouillon({ ...brouillon, titre: e.target.value })} />

        <div className="formulaire__paire">
          <div>
            <label htmlFor="ev-debut">Début</label>
            <input id="ev-debut" type="datetime-local" required value={brouillon.debut}
                   onChange={(e) => setBrouillon({ ...brouillon, debut: e.target.value })} />
          </div>
          <div>
            <label htmlFor="ev-fin">Fin (facultatif)</label>
            <input id="ev-fin" type="datetime-local" value={brouillon.fin}
                   onChange={(e) => setBrouillon({ ...brouillon, fin: e.target.value })} />
          </div>
        </div>

        <label htmlFor="ev-lieu">Lieu</label>
        <input id="ev-lieu" type="text" maxLength={200} value={brouillon.lieu}
               onChange={(e) => setBrouillon({ ...brouillon, lieu: e.target.value })} />

        <label htmlFor="ev-resume">Résumé</label>
        <textarea id="ev-resume" rows={2} maxLength={400} value={brouillon.resume}
                  onChange={(e) => setBrouillon({ ...brouillon, resume: e.target.value })} />

        <label htmlFor="ev-lien">Billetterie (http/https)</label>
        <input id="ev-lien" type="url" maxLength={512} value={brouillon.lien}
               placeholder="https://…"
               onChange={(e) => setBrouillon({ ...brouillon, lien: e.target.value })} />

        <label className="case">
          <input type="checkbox" checked={brouillon.complet}
                 onChange={(e) => setBrouillon({ ...brouillon, complet: e.target.checked })} />
          Complet
        </label>

        <div className="formulaire__actions">
          <button className="bouton" type="submit" disabled={enCours}>
            {edite ? 'Enregistrer' : 'Créer le brouillon'}
          </button>
          {edite && (
            <button className="lien" type="button" onClick={annulerEdition}>Abandonner</button>
          )}
        </div>
        {!edite && (
          <p className="aide">
            Un évènement est créé en brouillon : il n'apparaît sur aucune page
            publique tant qu'il n'est pas publié.
          </p>
        )}
      </form>
    </main>
  )
}
