/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  readonly VITE_OIDC_AUTHORITY?: string
  readonly VITE_OIDC_CLIENT?: string
  /**
   * Clé PUBLIABLE Stripe (pk_…). Publiable au sens strict : elle est faite
   * pour vivre dans le navigateur, et ne permet pas d'encaisser. La clé
   * secrète, elle, ne quitte jamais le serveur.
   */
  readonly VITE_STRIPE_CLE_PUBLIQUE?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
