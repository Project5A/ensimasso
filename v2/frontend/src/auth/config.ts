import type { UserManagerSettings } from 'oidc-client-ts'
import { WebStorageStateStore } from 'oidc-client-ts'

/**
 * Configuration OIDC.
 *
 * <p>Aucune implémentation de jeton maison : Keycloak émet, Spring valide, et
 * ce client se contente du flux standard. C'est ainsi que la v1 s'est retrouvée
 * avec `SECRET_KEY = "your_secret_key"` codé en dur dans le jar — en écrivant
 * elle-même ce que des bibliothèques éprouvées font correctement.
 */
export const parametresOidc: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/ensimasso',
  client_id: import.meta.env.VITE_OIDC_CLIENT ?? 'ensimasso-dashboard',
  redirect_uri: `${window.location.origin}/connexion/retour`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile email',

  // PKCE : obligatoire pour un client public. Sans lui, un code d'autorisation
  // intercepté suffit à obtenir un jeton.
  response_mode: 'query',

  // Le jeton vit en mémoire (sessionStorage pour survivre à un rechargement
  // d'onglet, pas au navigateur). La v1 stockait le JWT — et l'empreinte du
  // mot de passe de l'utilisateur — dans localStorage, lisible par n'importe
  // quel script tiers, dont l'embed Chatbase chargé sur chaque page.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  monitorSession: false,
}
