import { FournisseurAuth } from '../auth/AuthContext'
import Adherer from './Adherer'
import MesCommandes from './MesCommandes'
import Paiement from './Paiement'

/**
 * Les pages d'adhésion, chacune enveloppée dans son fournisseur OIDC.
 *
 * <p>Le fournisseur reste chargé À LA DEMANDE, comme pour le tableau de bord :
 * s'il entrait dans le chunk d'entrée, chaque visiteur d'une page publique
 * téléchargerait `oidc-client-ts` sans jamais s'en servir. C'est le reproche
 * fait à la v1, qui servait three.js et un modèle 3-D de 2,9 Mo à tout le
 * monde.
 *
 * <p>Chaque page s'enveloppe elle-même plutôt que de passer par un
 * {@code <Routes>} imbriqué : imbriqué, il ne verrait que la portion de chemin
 * restante, et ces routes-là n'ont pas de préfixe commun.
 */
export function PageAdherer() {
  return <FournisseurAuth><Adherer /></FournisseurAuth>
}

export function PageMesCommandes() {
  return <FournisseurAuth><MesCommandes /></FournisseurAuth>
}

export function PagePaiement() {
  return <FournisseurAuth><Paiement /></FournisseurAuth>
}
