import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  apiDashboard,
  ErreurApi,
  type CampagneVue,
  type TarifVue,
} from '../api'
import { useAuth } from '../auth/AuthContext'

const LIBELLE_CIBLE: Record<string, string> = {
  ETUDIANT: 'Étudiant·e', EXTERIEUR: 'Extérieur', ANCIEN: 'Ancien·ne',
}

export function euros(cents: number): string {
  return cents === 0 ? 'Gratuit' : `${(cents / 100).toFixed(2).replace('.', ',')} €`
}

/**
 * Adhérer à une association.
 *
 * <p>Le bloc CTA_ADHESION du portail public pointe vers
 * {@code /assos/{slug}/adherer} depuis toujours. Cette route n'existait pas :
 * elle tombait sur {@code /assos/:slug/:pageSlug}, donc sur la recherche d'une
 * page nommée « adherer », donc sur une erreur. Le seul bouton d'adhésion du
 * site public ne menait nulle part.
 *
 * <p>Un tarif payant passe par la COMMANDE, qui crée l'intention de paiement
 * en même temps que l'adhésion. Un tarif gratuit passe par la route directe,
 * qui active immédiatement — il n'y a rien à encaisser.
 */
export default function Adherer() {
  const { slug = '' } = useParams()
  const { jeton, utilisateur, chargement, connecter } = useAuth()
  const naviguer = useNavigate()

  const refJeton = useRef(jeton)
  refJeton.current = jeton

  // `connecte` plutôt que `utilisateur` dans les dépendances : l'effet ne
  // s'intéresse qu'au FAIT d'être connecté, et un objet change d'identité à
  // chaque rendu — l'effet se rappellerait alors sans fin, puisqu'il écrit
  // dans l'état. Même précaution que pour `jeton`.
  const connecte = utilisateur != null

  const [campagne, setCampagne] = useState<CampagneVue | null>(null)
  const [tarifs, setTarifs] = useState<TarifVue[] | null>(null)
  const [nom, setNom] = useState(slug)
  const [erreur, setErreur] = useState<string | null>(null)
  const [succes, setSucces] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  useEffect(() => {
    if (!connecte) return
    let vivant = true
    ;(async () => {
      const assos = await apiDashboard.associations(refJeton.current())
      const asso = assos.find((a) => a.slug === slug)
      if (!asso) throw new ErreurApi(404, "Cette association n'existe pas.")
      if (!vivant) return
      setNom(asso.nom)

      const ouvertes = (await apiDashboard.campagnes(refJeton.current(), asso.id))
        .filter((c) => c.statut === 'OUVERTE')
      if (!vivant) return
      const ouverte = ouvertes[0] ?? null
      setCampagne(ouverte)
      setTarifs(ouverte ? await apiDashboard.tarifs(refJeton.current(), ouverte.id) : [])
    })().catch((e: unknown) => {
      if (vivant) setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible')
    })
    return () => { vivant = false }
  }, [slug, connecte])

  async function choisir(tarif: TarifVue) {
    if (enCours || !campagne) return
    setEnCours(true)
    setErreur(null)
    try {
      if (tarif.montantCents === 0) {
        // Rien à encaisser : l'adhésion s'active immédiatement, et ouvrir une
        // commande à zéro euro n'aurait aucun objet.
        await apiDashboard.adhererGratuitement(jeton(), campagne.id, tarif.publicCible)
        setSucces(`Vous êtes adhérent·e de ${nom} pour ${campagne.couvreAnneeCode}.`)
      } else {
        // LA porte des adhésions payantes : l'adhésion, la commande et
        // l'intention de paiement naissent ensemble. Aucun montant ne part
        // d'ici — le prix vit dans les tarifs, côté serveur.
        const commande = await apiDashboard.commanderAdhesion(
          jeton(), campagne.id, tarif.publicCible)
        naviguer(`/commandes/${commande.id}`)
      }
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
    } finally {
      setEnCours(false)
    }
  }

  if (chargement) {
    return <main className="page page--centree"><p aria-live="polite">Chargement…</p></main>
  }

  if (!utilisateur) {
    return (
      <main className="page page--centree">
        <h1>Adhérer à {nom}</h1>
        <p>Il faut être connecté·e pour adhérer : l'adhésion est nominative.</p>
        <button className="bouton" onClick={() => void connecter()}>Se connecter</button>
      </main>
    )
  }

  return (
    <main className="page page--centree">
      <h1>Adhérer à {nom}</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}

      {succes ? (
        <>
          <p role="status" className="info">{succes}</p>
          <p><Link to={`/assos/${slug}`}>Retour au site de {nom}</Link></p>
        </>
      ) : (
        <>
          {!tarifs && !erreur && <p aria-live="polite">Chargement…</p>}

          {tarifs && !campagne && (
            <div className="vide-etat">
              <h2>Aucune campagne ouverte</h2>
              <p>
                Cette association n'ouvre pas les adhésions en ce moment.
                Revenez à la rentrée.
              </p>
            </div>
          )}

          {campagne && tarifs?.length === 0 && (
            <div className="vide-etat">
              <h2>Campagne ouverte, aucun tarif</h2>
              <p>Le bureau n'a pas encore fixé ses tarifs.</p>
            </div>
          )}

          {campagne && tarifs && tarifs.length > 0 && (
            <>
              <p className="aide">
                Adhésion pour l'année {campagne.couvreAnneeCode}. Choisissez le
                tarif qui vous correspond : c'est votre statut qui le détermine,
                et le serveur vérifie que vous y avez droit.
              </p>
              <ul className="tarifs tarifs--choix">
                {tarifs.map((t) => (
                  <li key={t.id}>
                    <button className="bouton" type="button" disabled={enCours}
                            onClick={() => void choisir(t)}>
                      {LIBELLE_CIBLE[t.publicCible] ?? t.publicCible} — {t.libelle} :{' '}
                      {euros(t.montantCents)}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </main>
  )
}
