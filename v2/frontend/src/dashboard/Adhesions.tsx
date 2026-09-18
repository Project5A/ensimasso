import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiDashboard,
  ErreurApi,
  PUBLICS_CIBLES,
  type AdhesionVue,
  type CampagneVue,
  type TarifVue,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { champVersIso } from './dates'
import { FilMandat, NavMandat } from './NavMandat'

const LIBELLE_CIBLE: Record<string, string> = {
  ETUDIANT: 'Étudiant·e', EXTERIEUR: 'Extérieur', ANCIEN: 'Ancien·ne',
}

const LIBELLE_STATUT: Record<string, string> = {
  PREPAREE: 'Préparée', OUVERTE: 'Ouverte', FERMEE: 'Fermée',
  EN_ATTENTE_PAIEMENT: 'En attente de paiement', ACTIVE: 'Active',
  ANNULEE: 'Annulée', REMBOURSEE: 'Remboursée',
}

function euros(cents: number): string {
  return `${(cents / 100).toFixed(2).replace('.', ',')} €`
}

/**
 * Les campagnes d'adhésion, côté bureau.
 *
 * <p>Le module `adhesion` était complet — campagnes, tarifs par public,
 * « early bird » de juillet couvrant l'année suivante, liste nominative
 * réservée — et aucun écran ne l'appelait. Un bureau ne pouvait donc pas
 * ouvrir sa campagne autrement qu'avec curl, et le bloc CTA_ADHESION du
 * portail public menait à une route qui n'existait pas.
 */
export default function Adhesions() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const refJeton = useRef(jeton)
  refJeton.current = jeton

  const [contexte, setContexte] =
    useState<{ associationId: string; anneeCode: string } | null>(null)
  const [campagnes, setCampagnes] = useState<CampagneVue[] | null>(null)
  const [tarifs, setTarifs] = useState<Record<string, TarifVue[]>>({})
  const [adherents, setAdherents] = useState<AdhesionVue[]>([])
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  const [annee, setAnnee] = useState('')
  const [ferme, setFerme] = useState('')
  const [tarif, setTarif] = useState({ libelle: '', montant: '', cible: 'ETUDIANT' })

  useEffect(() => {
    apiDashboard
      .mesPostes(refJeton.current())
      .then((postes) => {
        const p = postes.find((x) => x.mandatId === mandatId)
        if (p) {
          setContexte({ associationId: p.associationId, anneeCode: p.anneeCode })
          setAnnee(p.anneeCode)
        } else setErreur("Ce mandat n'est pas le vôtre.")
      })
      .catch((e: unknown) =>
        setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [mandatId])

  const charger = useCallback(async () => {
    if (!contexte) return
    const liste = await apiDashboard.campagnes(refJeton.current(), contexte.associationId)
    setCampagnes(liste)
    const paires = await Promise.all(
      liste.map(async (c) =>
        [c.id, await apiDashboard.tarifs(refJeton.current(), c.id)] as const))
    setTarifs(Object.fromEntries(paires))
    setAdherents(
      await apiDashboard.adherents(
        refJeton.current(), contexte.associationId, contexte.anneeCode))
  }, [contexte])

  useEffect(() => {
    if (!contexte) return
    charger().catch((e: unknown) =>
      setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [contexte, charger])

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

  const ouverte = campagnes?.find((c) => c.statut === 'OUVERTE') ?? null
  const actives = adherents.filter((a) => a.statut === 'ACTIVE')
  const encaisse = actives.reduce((t, a) => t + a.montantPayeCents, 0)

  return (
    <main className="page">
      <FilMandat section="Adhésions" />
      <NavMandat mandatId={mandatId} actif="adhesions" />

      <h1>Adhésions</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}
      {!campagnes && !erreur && <p aria-live="polite">Chargement…</p>}

      {campagnes && (
        <section>
          <h2>Ouvrir une campagne</h2>
          <p className="aide">
            L'année indiquée est celle que l'adhésion COUVRE, pas celle où on la
            vend : une campagne ouverte en juillet pour l'année suivante est
            exactement ce que le modèle sait représenter, et chaque vente sera
            portée au compte du bureau en fonction ce jour-là.
          </p>
          <form
            className="formulaire"
            onSubmit={(e) => {
              e.preventDefault()
              if (!contexte) return
              void agir(
                () => apiDashboard.ouvrirCampagne(
                  jeton(), contexte.associationId, annee.trim(), champVersIso(ferme)),
                'Campagne ouverte.')
            }}
          >
            <label htmlFor="ad-annee">Année couverte</label>
            <input id="ad-annee" type="text" value={annee} placeholder="2026-2027"
                   pattern="\d{4}-\d{4}"
                   onChange={(e) => setAnnee(e.target.value)} />

            <label htmlFor="ad-ferme">Fermeture (facultatif)</label>
            <input id="ad-ferme" type="datetime-local" value={ferme}
                   onChange={(e) => setFerme(e.target.value)} />

            <div className="formulaire__actions">
              <button className="bouton" type="submit" disabled={enCours || !contexte}>
                Ouvrir
              </button>
            </div>
          </form>
        </section>
      )}

      {campagnes && campagnes.length > 0 && (
        <section>
          <h2>Campagnes</h2>
          <ul className="campagnes">
            {campagnes.map((c) => (
              <li key={c.id} className="campagne">
                <div className="campagne__entete">
                  <span className="mandats__annee">{c.couvreAnneeCode}</span>
                  <span className="mandats__statut">{LIBELLE_STATUT[c.statut]}</span>
                  {c.statut === 'OUVERTE' && (
                    <button className="lien lien-danger" type="button" disabled={enCours}
                            onClick={() => void agir(
                              () => apiDashboard.fermerCampagne(jeton(), c.id),
                              'Campagne fermée.')}>
                      Fermer
                    </button>
                  )}
                </div>

                <ul className="tarifs">
                  {(tarifs[c.id] ?? []).map((t) => (
                    <li key={t.id}>
                      {LIBELLE_CIBLE[t.publicCible] ?? t.publicCible} — {t.libelle} :{' '}
                      <strong>{euros(t.montantCents)}</strong>
                    </li>
                  ))}
                  {(tarifs[c.id] ?? []).length === 0 && (
                    <li className="aide">
                      Aucun tarif : personne ne peut adhérer tant qu'il n'y en a pas.
                    </li>
                  )}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}

      {ouverte && (
        <section>
          <h2>Ajouter un tarif</h2>
          <p className="aide">
            Un seul tarif par public et par campagne. Le prix vit ici, côté
            serveur : il n'est jamais accepté depuis le navigateur de
            l'adhérent.
          </p>
          <form
            className="formulaire"
            onSubmit={(e) => {
              e.preventDefault()
              const cents = Math.round(Number.parseFloat(tarif.montant.replace(',', '.')) * 100)
              if (!Number.isFinite(cents) || cents < 0) {
                setErreur('Le montant doit être un nombre.')
                return
              }
              void agir(async () => {
                await apiDashboard.definirTarif(
                  jeton(), ouverte.id, tarif.libelle.trim(), cents, tarif.cible)
                setTarif({ libelle: '', montant: '', cible: 'ETUDIANT' })
              }, 'Tarif enregistré.')
            }}
          >
            <label htmlFor="ta-libelle">Libellé</label>
            <input id="ta-libelle" type="text" maxLength={120} value={tarif.libelle}
                   onChange={(e) => setTarif({ ...tarif, libelle: e.target.value })} />

            <label htmlFor="ta-montant">Montant en euros</label>
            <input id="ta-montant" type="text" inputMode="decimal" value={tarif.montant}
                   placeholder="15,00"
                   onChange={(e) => setTarif({ ...tarif, montant: e.target.value })} />

            <label htmlFor="ta-cible">Public</label>
            <select id="ta-cible" value={tarif.cible}
                    onChange={(e) => setTarif({ ...tarif, cible: e.target.value })}>
              {PUBLICS_CIBLES.map((c) => (
                <option key={c} value={c}>{LIBELLE_CIBLE[c]}</option>
              ))}
            </select>

            <div className="formulaire__actions">
              <button className="bouton" type="submit" disabled={enCours}>Ajouter</button>
            </div>
          </form>
        </section>
      )}

      <section>
        <h2>Adhérents {contexte?.anneeCode}</h2>
        <p className="aide">
          {actives.length} adhésion{actives.length > 1 ? 's' : ''} active
          {actives.length > 1 ? 's' : ''}, {euros(encaisse)} encaissés.
          Liste nominative réservée au bureau.
        </p>
        {adherents.length === 0 ? (
          <p className="aide">Personne n'a encore adhéré pour cette année.</p>
        ) : (
          <ul className="mandats">
            {adherents.map((a) => (
              <li key={a.id}>
                <code className="mandats__annee">{a.personneId}</code>
                <span className="mandats__poste">
                  {LIBELLE_STATUT[a.statut]} — {euros(a.montantPayeCents)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
