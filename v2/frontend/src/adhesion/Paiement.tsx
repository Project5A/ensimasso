import { loadStripe, type Stripe, type StripeElements } from '@stripe/stripe-js'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi, type CommandeVue } from '../api'
import { useAuth } from '../auth/AuthContext'
import { euros } from './Adherer'

const CLE_PUBLIQUE = import.meta.env.VITE_STRIPE_CLE_PUBLIQUE ?? ''

/**
 * Payer une commande, avec les Elements de Stripe.
 *
 * <p>Le numéro de carte n'entre jamais dans cette page : les Elements sont des
 * cadres servis par Stripe, et le navigateur les envoie à Stripe directement.
 * C'est ce qui fait que ce dépôt n'a pas à être conforme PCI-DSS.
 *
 * <p>Le secret client est RELU auprès du serveur, jamais refabriqué : créer une
 * intention à chaque affichage laisserait chez Stripe une traînée d'intentions
 * toutes payables pour une seule commande — et une commande encaissée deux
 * fois.
 */
export default function Paiement() {
  const { commandeId = '' } = useParams()
  const { jeton, utilisateur, chargement, connecter } = useAuth()

  const refJeton = useRef(jeton)
  refJeton.current = jeton
  const monture = useRef<HTMLDivElement | null>(null)

  // `connecte` plutôt que `utilisateur` dans les dépendances : l'effet ne
  // s'intéresse qu'au FAIT d'être connecté, et un objet change d'identité à
  // chaque rendu — l'effet se rappellerait alors sans fin, puisqu'il écrit
  // dans l'état. Même précaution que pour `jeton`.
  const connecte = utilisateur != null

  const [commande, setCommande] = useState<CommandeVue | null>(null)
  const [stripe, setStripe] = useState<Stripe | null>(null)
  const [elements, setElements] = useState<StripeElements | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const [succes, setSucces] = useState(false)
  const [enCours, setEnCours] = useState(false)

  useEffect(() => {
    if (!connecte) return
    let vivant = true
    ;(async () => {
      const miennes = await apiDashboard.mesCommandes(refJeton.current())
      const c = miennes.find((x) => x.id === commandeId)
      if (!c) throw new ErreurApi(404, "Cette commande n'est pas la vôtre.")
      if (!vivant) return
      setCommande(c)

      if (c.statut !== 'OUVERTE') return
      if (!CLE_PUBLIQUE) {
        // Le dire plutôt que d'afficher un cadre vide : sans clé publiable,
        // Stripe.js ne peut pas se charger, et l'écran resterait muet.
        throw new ErreurApi(
          500, "Le paiement n'est pas configuré (VITE_STRIPE_CLE_PUBLIQUE absente).")
      }
      const { secretClient } = await apiDashboard.secretDeCommande(
        refJeton.current(), commandeId)
      const sdk = await loadStripe(CLE_PUBLIQUE)
      if (!sdk) throw new ErreurApi(502, 'Stripe.js n’a pas pu être chargé.')
      if (!vivant) return

      const els = sdk.elements({ clientSecret: secretClient })
      els.create('payment').mount(monture.current as HTMLElement)
      setStripe(sdk)
      setElements(els)
    })().catch((e: unknown) => {
      if (vivant) setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible')
    })
    return () => { vivant = false }
  }, [commandeId, connecte])

  async function payer(e: React.FormEvent) {
    e.preventDefault()
    if (!stripe || !elements || enCours) return
    setEnCours(true)
    setErreur(null)
    const { error } = await stripe.confirmPayment({
      elements,
      redirect: 'if_required',
    })
    setEnCours(false)
    if (error) {
      setErreur(error.message ?? 'Le paiement a été refusé.')
      return
    }
    // Le droit n'est PAS accordé ici : c'est le webhook signé qui l'accorde,
    // côté serveur, après vérification du montant réellement encaissé. Cette
    // page ne fait que dire que la carte est passée.
    setSucces(true)
  }

  if (chargement) {
    return <main className="page page--centree"><p aria-live="polite">Chargement…</p></main>
  }

  if (!utilisateur) {
    return (
      <main className="page page--centree">
        <h1>Paiement</h1>
        <p>Il faut être connecté·e pour payer votre commande.</p>
        <button className="bouton" onClick={() => void connecter()}>Se connecter</button>
      </main>
    )
  }

  return (
    <main className="page page--centree">
      <h1>Paiement</h1>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}

      {commande && (
        <p className="aide">
          Commande de <strong>{euros(commande.montantTotalCents)}</strong> —{' '}
          {commande.statut === 'OUVERTE' ? 'en attente de paiement' : commande.statut}
        </p>
      )}

      {succes && (
        <>
          <p role="status" className="info">
            Paiement accepté. Votre adhésion est activée dès que notre serveur
            reçoit la confirmation de Stripe — d'ordinaire en quelques secondes.
          </p>
          <p><Link to="/commandes">Voir mes commandes</Link></p>
        </>
      )}

      {commande?.statut === 'OUVERTE' && !succes && (
        <form className="formulaire" onSubmit={payer}>
          <div ref={monture} />
          <div className="formulaire__actions">
            <button className="bouton" type="submit" disabled={!stripe || enCours}>
              {enCours ? 'Paiement en cours…' : `Payer ${euros(commande.montantTotalCents)}`}
            </button>
            <button
              className="lien lien-danger" type="button" disabled={enCours}
              onClick={() => {
                setEnCours(true)
                apiDashboard
                  .annulerCommande(jeton(), commandeId)
                  .then(() => setCommande({ ...commande, statut: 'ANNULEE' }))
                  .catch((e: unknown) =>
                    setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible'))
                  .finally(() => setEnCours(false))
              }}
            >
              Renoncer à cette commande
            </button>
          </div>
        </form>
      )}

      {commande && commande.statut !== 'OUVERTE' && !succes && (
        <p><Link to="/commandes">Voir mes commandes</Link></p>
      )}
    </main>
  )
}
