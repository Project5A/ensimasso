import type { AssociationVue, PageRendue } from './types'

/**
 * L'unique client d'API.
 *
 * <p>Une seule base d'URL, venue de la configuration. La v1 en avait quatre en
 * parallèle : une instance axios, les valeurs par défaut globales d'axios
 * modifiées par effet de bord à l'import, des fetch() avec l'URL Azure en dur
 * dans huit fichiers, et des fetch() relatifs qui ne fonctionnaient nulle part.
 * L'URL du backend était recopiée dans huit fichiers.
 */
const BASE = import.meta.env.VITE_API_BASE ?? ''

export class ErreurApi extends Error {
  constructor(readonly statut: number, message: string) {
    super(message)
  }
}

async function get<T>(chemin: string): Promise<T> {
  const reponse = await fetch(`${BASE}${chemin}`, {
    headers: { Accept: 'application/json' },
  })

  if (!reponse.ok) {
    // Le backend répond en RFC 7807 : on lit « detail » quand il est là, et on
    // n'expose jamais un corps d'erreur brut à l'utilisateur.
    let detail = `Erreur ${reponse.status}`
    try {
      const probleme = (await reponse.json()) as { detail?: string; title?: string }
      detail = probleme.detail ?? probleme.title ?? detail
    } catch {
      /* réponse non JSON : on garde le message générique */
    }
    throw new ErreurApi(reponse.status, detail)
  }
  // Un corps VIDE n'est pas du JSON.
  //
  // `response.json()` sur une réponse sans corps lève une SyntaxError — qui
  // n'est pas une ErreurApi, donc elle traverse les écrans et ressort en
  // « Chargement impossible » sans dire pourquoi. C'est ce qui arrivait sur le
  // thème d'un mandat qui n'en a pas encore : le contrôleur rendait `null`, ce
  // que Spring traduit par un 200 à corps vide. Le tout premier usage de
  // l'écran était le seul à échouer.
  //
  // Le contrôleur rend maintenant 204, mais la garde reste ici : c'est le seul
  // endroit traversé par TOUTES les réponses, et la panne était silencieuse.
  const texte = await reponse.text()
  return (texte ? JSON.parse(texte) : null) as T
}

/** Requête authentifiée : le jeton est lu à l'appel, jamais capturé. */
async function authed<T>(chemin: string, jeton: string | null, init: RequestInit = {}): Promise<T> {
  if (!jeton) throw new ErreurApi(401, 'Session expirée, reconnectez-vous.')

  const reponse = await fetch(`${BASE}${chemin}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${jeton}`,
      ...init.headers,
    },
  })

  if (reponse.status === 204) return undefined as T

  if (!reponse.ok) {
    let detail = `Erreur ${reponse.status}`
    let erreurs: string[] | undefined
    try {
      const probleme = (await reponse.json()) as {
        detail?: string; title?: string; erreurs?: string[]
      }
      detail = probleme.detail ?? probleme.title ?? detail
      erreurs = probleme.erreurs
    } catch {
      /* réponse non JSON */
    }
    // Le backend renvoie les erreurs de schéma d'un bloc dans « erreurs » :
    // les remonter telles quelles est ce qui rend l'éditeur utilisable.
    throw new ErreurApi(reponse.status, erreurs?.length ? `${detail} — ${erreurs.join(' ; ')}` : detail)
  }
  // Un corps VIDE n'est pas du JSON.
  //
  // `response.json()` sur une réponse sans corps lève une SyntaxError — qui
  // n'est pas une ErreurApi, donc elle traverse les écrans et ressort en
  // « Chargement impossible » sans dire pourquoi. C'est ce qui arrivait sur le
  // thème d'un mandat qui n'en a pas encore : le contrôleur rendait `null`, ce
  // que Spring traduit par un 200 à corps vide. Le tout premier usage de
  // l'écran était le seul à échouer.
  //
  // Le contrôleur rend maintenant 204, mais la garde reste ici : c'est le seul
  // endroit traversé par TOUTES les réponses, et la panne était silencieuse.
  const texte = await reponse.text()
  return (texte ? JSON.parse(texte) : null) as T
}

export type TypeBlocVue = {
  type: string
  schemaVersion: number
  libelle: string
  categorie: string
  composantReact: string
  jsonSchema: string
  /** Payload avec lequel la palette crée un bloc de ce type. Vient du registre,
   *  pas d'une table écrite en dur ici : c'est ce qui permet d'ajouter un type
   *  de bloc sans toucher au portail. */
  payloadDefaut: string
}

export type ThemeVue = {
  id: string
  mandatId: string
  numero: number
  statut: 'BROUILLON' | 'PUBLIEE' | 'ARCHIVEE'
  /** Jetons de style, sérialisés : le portail les relit tels quels. */
  tokens: string
}

export type PosteVue = {
  associationId: string
  mandatId: string
  anneeCode: string
  poste: string
  /** PREPARATION | EN_FONCTION. Un mandat CLOS n'est jamais rendu ici. */
  statutMandat: string
}

export type PageVue = { id: string; mandatId: string; slug: string; titre: string; ordreMenu: number }
export type VersionVue = {
  id: string; pageId: string; numero: number; statut: string
  publieLe: string | null; note: string | null
}
export type BlocVue = {
  id: string; ordre: number; type: string; schemaVersion: number
  payload: string; visible: boolean
}

/** Un évènement vu du tableau de bord : il montre les brouillons, contrairement
 *  au portail public. */
export type EvenementDashboard = {
  id: string; slug: string; titre: string
  resume: string | null; description: string | null; lieu: string | null
  debutLe: string; finLe: string | null
  mediaKey: string | null; lien: string | null
  statut: 'BROUILLON' | 'PUBLIE' | 'ANNULE'
  complet: boolean; motifAnnulation: string | null
}

export type RedactionEvenement = {
  slug?: string
  titre: string
  resume: string | null
  description: string | null
  lieu: string | null
  debutLe: string
  finLe: string | null
  mediaKey: string | null
  lien: string | null
  complet: boolean
}

export type PartenaireDashboard = {
  id: string; nom: string
  niveau: 'OR' | 'ARGENT' | 'BRONZE' | 'SOUTIEN'
  logoMediaKey: string | null; url: string | null
  ordre: number; visible: boolean
}

export type RedactionPartenaire = Omit<PartenaireDashboard, 'id'>

export const api = {
  annuaire: () => get<AssociationVue[]>('/api/public/associations'),

  page: (slugAsso: string, slugPage = 'accueil') =>
    get<PageRendue>(`/api/public/associations/${slugAsso}/pages/${slugPage}`),

  /** Une archive est une autre année, pas un autre code. */
  archive: (slugAsso: string, annee: string, slugPage = 'accueil') =>
    get<PageRendue>(`/api/public/associations/${slugAsso}/annees/${annee}/pages/${slugPage}`),
}

/** Les appels du tableau de bord. Tous authentifiés, tous autorisés côté serveur. */
export const apiDashboard = {
  mesPostes: (j: string | null) => authed<PosteVue[]>('/api/gouvernance/moi/postes', j),

  catalogue: (j: string | null) => authed<TypeBlocVue[]>('/api/contenu/types-blocs', j),

  pages: (j: string | null, mandatId: string) =>
    authed<PageVue[]>(`/api/contenu/mandats/${mandatId}/pages`, j),

  creerPage: (j: string | null, mandatId: string, corps: { slug: string; titre: string; ordreMenu: number }) =>
    authed<PageVue>(`/api/contenu/mandats/${mandatId}/pages`, j, {
      method: 'POST', body: JSON.stringify(corps),
    }),

  ouvrirBrouillon: (j: string | null, pageId: string) =>
    authed<VersionVue>(`/api/contenu/pages/${pageId}/brouillon`, j, { method: 'POST' }),

  blocs: (j: string | null, versionId: string) =>
    authed<BlocVue[]>(`/api/contenu/versions/${versionId}/blocs`, j),

  ajouterBloc: (j: string | null, versionId: string, type: string, payload: unknown, ordre?: number) =>
    authed<BlocVue>(`/api/contenu/versions/${versionId}/blocs`, j, {
      method: 'POST', body: JSON.stringify({ type, payload, ordre }),
    }),

  modifierBloc: (j: string | null, blocId: string, payload: unknown) =>
    authed<BlocVue>(`/api/contenu/blocs/${blocId}`, j, {
      method: 'PUT', body: JSON.stringify({ payload }),
    }),

  supprimerBloc: (j: string | null, blocId: string) =>
    authed<void>(`/api/contenu/blocs/${blocId}`, j, { method: 'DELETE' }),

  reordonner: (j: string | null, versionId: string, blocs: string[]) =>
    authed<void>(`/api/contenu/versions/${versionId}/ordre`, j, {
      method: 'PUT', body: JSON.stringify({ blocs }),
    }),

  publier: (j: string | null, versionId: string) =>
    authed<VersionVue>(`/api/contenu/versions/${versionId}/publier`, j, { method: 'POST' }),

  /** L'aperçu d'un brouillon : même rendu que la page publique, hors /api/public. */
  apercu: (j: string | null, versionId: string) =>
    authed<PageRendue>(`/api/apercu/versions/${versionId}`, j),

  // ------------------------------------------------------------- agenda

  evenements: (j: string | null, mandatId: string) =>
    authed<EvenementDashboard[]>(`/api/agenda/mandats/${mandatId}/evenements`, j),

  creerEvenement: (j: string | null, mandatId: string, corps: RedactionEvenement) =>
    authed<EvenementDashboard>(`/api/agenda/mandats/${mandatId}/evenements`, j, {
      method: 'POST', body: JSON.stringify(corps),
    }),

  modifierEvenement: (j: string | null, id: string, corps: RedactionEvenement) =>
    authed<EvenementDashboard>(`/api/agenda/evenements/${id}`, j, {
      method: 'PUT', body: JSON.stringify(corps),
    }),

  publierEvenement: (j: string | null, id: string) =>
    authed<EvenementDashboard>(`/api/agenda/evenements/${id}/publier`, j, { method: 'POST' }),

  annulerEvenement: (j: string | null, id: string, motif: string) =>
    authed<EvenementDashboard>(`/api/agenda/evenements/${id}/annuler`, j, {
      method: 'POST', body: JSON.stringify({ motif }),
    }),

  supprimerEvenement: (j: string | null, id: string) =>
    authed<void>(`/api/agenda/evenements/${id}`, j, { method: 'DELETE' }),

  // -------------------------------------------------------- partenaires

  // --- thème du mandat ---
  theme: (j: string | null, mandatId: string) =>
    authed<ThemeVue | null>(`/api/contenu/mandats/${mandatId}/theme`, j),

  enregistrerTheme: (j: string | null, mandatId: string, tokens: Record<string, string>) =>
    authed<ThemeVue>(`/api/contenu/mandats/${mandatId}/theme`, j, {
      method: 'PUT', body: JSON.stringify({ tokens }),
    }),

  publierTheme: (j: string | null, mandatId: string) =>
    authed<ThemeVue>(`/api/contenu/mandats/${mandatId}/theme/publier`, j, { method: 'POST' }),

  partenaires: (j: string | null, mandatId: string) =>
    authed<PartenaireDashboard[]>(`/api/partenaires/mandats/${mandatId}`, j),

  creerPartenaire: (j: string | null, mandatId: string, corps: RedactionPartenaire) =>
    authed<PartenaireDashboard>(`/api/partenaires/mandats/${mandatId}`, j, {
      method: 'POST', body: JSON.stringify(corps),
    }),

  modifierPartenaire: (j: string | null, id: string, corps: RedactionPartenaire) =>
    authed<PartenaireDashboard>(`/api/partenaires/${id}`, j, {
      method: 'PUT', body: JSON.stringify(corps),
    }),

  supprimerPartenaire: (j: string | null, id: string) =>
    authed<void>(`/api/partenaires/${id}`, j, { method: 'DELETE' }),
}
