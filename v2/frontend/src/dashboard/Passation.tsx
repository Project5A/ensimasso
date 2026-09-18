import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiDashboard,
  ErreurApi,
  POSTES,
  type MembreVue,
  type PassationVue,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { champVersIso } from './dates'
import { FilMandat, NavMandat } from './NavMandat'

const LIBELLE_POSTE: Record<string, string> = {
  PRESIDENT: 'Président·e', VICE_PRESIDENT: 'Vice-président·e',
  TRESORIER: 'Trésorier·ère', SECRETAIRE: 'Secrétaire',
  RESP_COM: 'Responsable communication', RESP_EVENEMENTS: 'Responsable évènements',
  MEMBRE_BUREAU: 'Membre du bureau',
}

const LIBELLE_STATUT: Record<PassationVue['statut'], string> = {
  PREPAREE: 'Préparée — le bureau entrant se compose',
  BUREAU_COMPLETE: 'Bureau complet — reste l’investiture',
  ACTIVEE: 'Activée',
  ANNULEE: 'Annulée',
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * La passation, de bout en bout.
 *
 * <p>Les quatre étapes existaient côté serveur — préparer, désigner, marquer le
 * bureau complet, activer — et aucune n'était atteignable autrement qu'avec
 * curl. Le statut PRÉPARATION d'un mandat, le clonage des pages publiées et du
 * thème, la contrainte d'exclusion qui interdit deux bureaux simultanés :
 * tout cela ne servait à rien, faute d'un écran pour le déclencher.
 *
 * <p>Une lecture manquait aussi : les quatre routes faisaient AVANCER une
 * passation, aucune ne disait où elle en était.
 */
export default function Passation() {
  const { mandatId = '' } = useParams()
  const { jeton, utilisateur } = useAuth()

  const refJeton = useRef(jeton)
  refJeton.current = jeton

  const [associationId, setAssociationId] = useState<string | null>(null)
  const [passations, setPassations] = useState<PassationVue[] | null>(null)
  const [bureauEntrant, setBureauEntrant] = useState<MembreVue[]>([])
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  const [debut, setDebut] = useState('')
  const [fin, setFin] = useState('')
  const [nouveau, setNouveau] = useState({ personneId: '', poste: 'PRESIDENT', ordre: '0' })
  const [dateAg, setDateAg] = useState('')

  useEffect(() => {
    apiDashboard
      .mesPostes(refJeton.current())
      .then((postes) => {
        const p = postes.find((x) => x.mandatId === mandatId)
        if (p) setAssociationId(p.associationId)
        else setErreur("Ce mandat n'est pas le vôtre.")
      })
      .catch((e: unknown) =>
        setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [mandatId])

  const charger = useCallback(async () => {
    if (!associationId) return
    const liste = await apiDashboard.passations(refJeton.current(), associationId)
    setPassations(liste)
    const ouverte = liste.find((p) => p.statut === 'PREPAREE' || p.statut === 'BUREAU_COMPLETE')
    setBureauEntrant(
      ouverte ? await apiDashboard.bureau(refJeton.current(), ouverte.mandatEntrantId) : [])
  }, [associationId])

  useEffect(() => {
    if (!associationId) return
    charger().catch((e: unknown) =>
      setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [associationId, charger])

  async function agir(action: () => Promise<unknown>, message?: string) {
    if (enCours) return
    setEnCours(true)
    setErreur(null)
    setInfo(null)
    try {
      await action()
      await charger()
      if (message) setInfo(message)
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
    } finally {
      setEnCours(false)
    }
  }

  const ouverte = passations?.find(
    (p) => p.statut === 'PREPAREE' || p.statut === 'BUREAU_COMPLETE') ?? null
  const monIdentifiant = utilisateur?.profile.sub ?? ''
  const terminees = (passations ?? []).filter(
    (p) => p.statut === 'ACTIVEE' || p.statut === 'ANNULEE')

  return (
    <main className="page">
      <FilMandat section="Passation" />
      <NavMandat mandatId={mandatId} actif="passation" />

      <h1>Passation</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}

      {!passations && !erreur && <p aria-live="polite">Chargement…</p>}

      {passations && !ouverte && (
        <section>
          <h2>Préparer la passation</h2>
          <p className="aide">
            Un mandat est créé pour l'année suivante, et les pages publiées du
            bureau actuel y sont recopiées en brouillon, thème compris. Rien
            n'est publié : le bureau entrant repart de votre site, et le modifie.
          </p>
          <form
            className="formulaire"
            onSubmit={(e) => {
              e.preventDefault()
              const debutLe = champVersIso(debut)
              if (!associationId || !debutLe) {
                setErreur('Une date de début est nécessaire.')
                return
              }
              void agir(
                () => apiDashboard.preparerPassation(
                  jeton(), associationId, debutLe, champVersIso(fin)),
                'Passation préparée.')
            }}
          >
            <label htmlFor="pa-debut">Début prévu du mandat entrant</label>
            <input id="pa-debut" type="datetime-local" value={debut}
                   onChange={(e) => setDebut(e.target.value)} />

            <label htmlFor="pa-fin">Fin prévue (facultatif)</label>
            <input id="pa-fin" type="datetime-local" value={fin}
                   onChange={(e) => setFin(e.target.value)} />

            <div className="formulaire__actions">
              <button className="bouton" type="submit" disabled={enCours || !associationId}>
                Préparer
              </button>
            </div>
          </form>
        </section>
      )}

      {ouverte && (
        <section>
          <h2>Passation en cours</h2>
          <p className="passation__statut">{LIBELLE_STATUT[ouverte.statut]}</p>
          <p className="aide">
            {ouverte.pagesClonees} page{ouverte.pagesClonees > 1 ? 's' : ''} recopiée
            {ouverte.pagesClonees > 1 ? 's' : ''} en brouillon pour le bureau entrant.
          </p>

          <h3>Bureau entrant</h3>
          {bureauEntrant.length === 0 ? (
            <p className="aide">Personne n'est encore désigné.</p>
          ) : (
            <ul className="mandats">
              {bureauEntrant.map((m) => (
                <li key={m.id}>
                  <span className="mandats__annee">
                    {LIBELLE_POSTE[m.poste] ?? m.poste}
                  </span>
                  <code className="mandats__poste">{m.personneId}</code>
                </li>
              ))}
            </ul>
          )}

          <form
            className="formulaire"
            onSubmit={(e) => {
              e.preventDefault()
              const id = nouveau.personneId.trim()
              if (!UUID.test(id)) {
                setErreur("L'identifiant doit être celui du compte de la personne.")
                return
              }
              void agir(async () => {
                await apiDashboard.designer(
                  jeton(), ouverte.id, id, nouveau.poste,
                  Number.parseInt(nouveau.ordre, 10) || 0)
                setNouveau({ personneId: '', poste: 'MEMBRE_BUREAU', ordre: '0' })
              }, 'Membre désigné.')
            }}
          >
            <h3>Désigner un membre</h3>

            {/* Rien ne permet de chercher quelqu'un par son nom : le système ne
                stocke aucun nom, seulement le « sub » Keycloak. Chacun lit le
                sien ici et le transmet — ce n'est pas un annuaire, et il faut le
                dire plutôt que de faire semblant. */}
            <p className="aide">
              Il n'existe pas d'annuaire : une personne se désigne par
              l'identifiant de son compte. Le vôtre est <code>{monIdentifiant}</code> —
              demandez le sien à chaque membre entrant, il le trouve au même endroit.
            </p>

            <label htmlFor="pa-personne">Identifiant du compte</label>
            <input id="pa-personne" type="text" value={nouveau.personneId}
                   placeholder="00000000-0000-0000-0000-000000000000"
                   onChange={(e) => setNouveau({ ...nouveau, personneId: e.target.value })} />

            <label htmlFor="pa-poste">Poste</label>
            <select id="pa-poste" value={nouveau.poste}
                    onChange={(e) => setNouveau({ ...nouveau, poste: e.target.value })}>
              {POSTES.map((p) => (
                <option key={p} value={p}>{LIBELLE_POSTE[p]}</option>
              ))}
            </select>

            <label htmlFor="pa-ordre">Rang d'affichage</label>
            <input id="pa-ordre" type="number" min={0} value={nouveau.ordre}
                   onChange={(e) => setNouveau({ ...nouveau, ordre: e.target.value })} />

            <div className="formulaire__actions">
              <button className="bouton" type="submit" disabled={enCours}>Désigner</button>
            </div>
          </form>

          <div className="formulaire__actions">
            {ouverte.statut === 'PREPAREE' && (
              <button
                className="bouton" type="button" disabled={enCours}
                onClick={() => void agir(
                  () => apiDashboard.bureauComplet(jeton(), ouverte.id),
                  'Bureau déclaré complet.')}
              >
                Le bureau est complet
              </button>
            )}

            <button
              className="lien lien-danger" type="button" disabled={enCours}
              onClick={() => void agir(
                () => apiDashboard.annulerPassation(jeton(), ouverte.id),
                'Passation annulée.')}
            >
              Annuler la passation
            </button>
          </div>

          {ouverte.statut === 'BUREAU_COMPLETE' && (
            <form
              className="formulaire"
              onSubmit={(e) => {
                e.preventDefault()
                void agir(
                  () => apiDashboard.activerPassation(jeton(), ouverte.id, champVersIso(dateAg)),
                  'Bureau entrant investi.')
              }}
            >
              <h3>Investiture</h3>
              <p className="aide">
                Le mandat sortant est clos et le mandat entrant investi dans la
                même transaction. À partir de cet instant, c'est le site du
                bureau entrant qui est public, et le vôtre devient une archive.
              </p>
              <label htmlFor="pa-ag">Date de l'assemblée générale</label>
              <input id="pa-ag" type="datetime-local" value={dateAg}
                     onChange={(e) => setDateAg(e.target.value)} />
              <div className="formulaire__actions">
                <button className="bouton" type="submit" disabled={enCours}>
                  Investir le bureau entrant
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {terminees.length > 0 && (
        <section>
          <h2>Historique</h2>
          {/* Seulement ce qui est TERMINÉ : reprendre ici la passation en cours
              la ferait apparaître deux fois sur le même écran, sous le même
              libellé, à deux endroits qui appellent des gestes différents. */}
          <ul className="mandats">
            {terminees.map((p) => (
              <li key={p.id}>
                <span className="mandats__annee">{LIBELLE_STATUT[p.statut]}</span>
                <span className="mandats__poste">
                  {p.pagesClonees} page{p.pagesClonees > 1 ? 's' : ''} recopiée
                  {p.pagesClonees > 1 ? 's' : ''}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}
