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

// ----------------------------------------------------------- adhésion

export type CampagneVue = {
  id: string
  associationId: string
  couvreAnneeCode: string
  statut: 'PREPAREE' | 'OUVERTE' | 'FERMEE'
  ouvreLe: string | null
  fermeLe: string | null
}

export type TarifVue = {
  id: string
  libelle: string
  montantCents: number
  publicCible: 'ETUDIANT' | 'EXTERIEUR' | 'ANCIEN'
}

export type AdhesionVue = {
  id: string
  personneId: string
  associationId: string
  couvreAnneeCode: string
  montantPayeCents: number
  statut: 'EN_ATTENTE_PAIEMENT' | 'ACTIVE' | 'ANNULEE' | 'REMBOURSEE'
  activeeLe: string | null
}

export type CommandeVue = {
  id: string
  associationId: string
  statut: 'OUVERTE' | 'PAYEE' | 'ANNULEE' | 'REMBOURSEE'
  montantTotalCents: number
  devise: string
  payeeLe: string | null
}

export type EcritureVue = {
  id: string
  sens: 'ENTREE' | 'SORTIE'
  montantCents: number
  motif: string
  creeLe: string
}

export type JournalVue = { soldeCents: number; ecritures: EcritureVue[] }

export type AssociationBrute = { id: string; slug: string; nom: string; type: string }

export const PUBLICS_CIBLES = ['ETUDIANT', 'EXTERIEUR', 'ANCIEN'] as const

// ----------------------------------------------------------- passation

export type MembreVue = {
  id: string
  personneId: string
  poste: string
  titreAffiche: string | null
  ordre: number
  photoMediaKey: string | null
}

export type PassationVue = {
  id: string
  associationId: string
  mandatSortantId: string | null
  mandatEntrantId: string
  statut: 'PREPAREE' | 'BUREAU_COMPLETE' | 'ACTIVEE' | 'ANNULEE'
  pagesClonees: number
}

export const POSTES = [
  'PRESIDENT', 'VICE_PRESIDENT', 'TRESORIER', 'SECRETAIRE',
  'RESP_COM', 'RESP_EVENEMENTS', 'MEMBRE_BUREAU',
] as const

// ------------------------------------------------------------- médias

/** Un média de la médiathèque, tel que le serveur le décrit. */
export type MediaVue = {
  id: string
  cle: string
  contentType: string
  tailleOctets: number | null
  largeur: number | null
  hauteur: number | null
  blurhash: string | null
  texteAlternatif: string | null
  statut: 'ATTENTE_DEPOT' | 'DISPONIBLE' | 'REJETE' | 'SUPPRIME'
}

/** Ce que le serveur rend pour déposer un fichier SANS passer par lui. */
export type DepotVue = {
  mediaId: string
  cle: string
  url: string
  methode: string
  enTetes: Record<string, string>
  expireLe: string
  tailleMaxOctets: number
}

/** Les types que le serveur accepte — ServiceMedia.TYPES_AUTORISES. */
export const TYPES_MEDIA_ACCEPTES = [
  'image/jpeg', 'image/png', 'image/webp', 'image/avif', 'image/gif', 'application/pdf',
] as const

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

  // ------------------------------------------------------------- médias

  medias: (j: string | null, associationId: string, anneeCode: string) =>
    authed<MediaVue[]>(
      `/api/medias/associations/${associationId}/annees/${anneeCode}`, j),

  preparerDepot: (j: string | null, associationId: string, nomOriginal: string, contentType: string) =>
    authed<DepotVue>('/api/medias/depots', j, {
      method: 'POST',
      body: JSON.stringify({ associationId, nomOriginal, contentType }),
    }),

  confirmerDepot: (j: string | null, mediaId: string) =>
    authed<MediaVue>(`/api/medias/${mediaId}/confirmer`, j, { method: 'POST' }),

  decrireMedia: (j: string | null, mediaId: string, texteAlternatif: string) =>
    authed<MediaVue>(`/api/medias/${mediaId}/description`, j, {
      method: 'PUT', body: JSON.stringify({ texteAlternatif }),
    }),

  supprimerMedia: (j: string | null, mediaId: string) =>
    authed<void>(`/api/medias/${mediaId}`, j, { method: 'DELETE' }),

  /**
   * Résout des clés en URL de lecture.
   *
   * <p>En lot, et jamais mises en cache au-delà de leur durée de vie : la CLÉ
   * est la référence stable, l'URL est signée et expire. Un écran qui
   * stockerait ces URL afficherait des images mortes une demi-heure plus tard.
   */
  urlsMedias: (j: string | null, cles: string[]) =>
    cles.length === 0
      ? Promise.resolve({} as Record<string, string>)
      : authed<Record<string, string>>('/api/medias/urls', j, {
          method: 'POST', body: JSON.stringify({ cles }),
        }),
  // ----------------------------------------------------------- passation

  bureau: (j: string | null, mandatId: string) =>
    authed<MembreVue[]>(`/api/gouvernance/mandats/${mandatId}/bureau`, j),

  passations: (j: string | null, associationId: string) =>
    authed<PassationVue[]>(`/api/passations?associationId=${associationId}`, j),

  preparerPassation: (
    j: string | null, associationId: string, debutPrevu: string, finPrevue: string | null,
  ) =>
    authed<PassationVue>('/api/passations', j, {
      method: 'POST', body: JSON.stringify({ associationId, debutPrevu, finPrevue }),
    }),

  designer: (j: string | null, passationId: string, personneId: string, poste: string, ordre: number) =>
    authed<void>(`/api/passations/${passationId}/membres`, j, {
      method: 'POST', body: JSON.stringify({ personneId, poste, ordre }),
    }),

  bureauComplet: (j: string | null, passationId: string) =>
    authed<PassationVue>(`/api/passations/${passationId}/bureau-complet`, j, { method: 'POST' }),

  activerPassation: (j: string | null, passationId: string, aLAg: string | null) =>
    authed<PassationVue>(`/api/passations/${passationId}/activer`, j, {
      method: 'POST', body: JSON.stringify({ aLAg }),
    }),

  annulerPassation: (j: string | null, passationId: string) =>
    authed<void>(`/api/passations/${passationId}`, j, { method: 'DELETE' }),
  // ----------------------------------------------------------- adhésion

  associations: (j: string | null) =>
    authed<AssociationBrute[]>('/api/gouvernance/associations', j),

  campagnes: (j: string | null, associationId: string) =>
    authed<CampagneVue[]>(`/api/adhesions/associations/${associationId}/campagnes`, j),

  ouvrirCampagne: (j: string | null, associationId: string, couvreAnneeCode: string,
                   fermeLe: string | null) =>
    authed<CampagneVue>('/api/adhesions/campagnes', j, {
      method: 'POST', body: JSON.stringify({ associationId, couvreAnneeCode, fermeLe }),
    }),

  fermerCampagne: (j: string | null, campagneId: string) =>
    authed<void>(`/api/adhesions/campagnes/${campagneId}`, j, { method: 'DELETE' }),

  tarifs: (j: string | null, campagneId: string) =>
    authed<TarifVue[]>(`/api/adhesions/campagnes/${campagneId}/tarifs`, j),

  definirTarif: (j: string | null, campagneId: string, libelle: string,
                 montantCents: number, publicCible: string) =>
    authed<TarifVue>(`/api/adhesions/campagnes/${campagneId}/tarifs`, j, {
      method: 'POST', body: JSON.stringify({ libelle, montantCents, publicCible }),
    }),

  adherents: (j: string | null, associationId: string, anneeCode: string) =>
    authed<AdhesionVue[]>(
      `/api/adhesions/associations/${associationId}/annees/${anneeCode}`, j),

  mesAdhesions: (j: string | null) =>
    authed<AdhesionVue[]>('/api/adhesions/moi', j),

  /**
   * Commander une adhésion.
   *
   * <p>C'est LA porte d'entrée d'une adhésion payante : elle crée l'adhésion,
   * la commande et l'intention de paiement ensemble. La route
   * /api/adhesions/campagnes/{id}/adherer, elle, ne crée aucune commande et
   * refuse désormais un tarif payant — une adhésion payante sans commande ne
   * pourrait ni être payée ni être abandonnée.
   */
  commanderAdhesion: (j: string | null, campagneId: string, publicCible: string) =>
    authed<CommandeVue>('/api/tresorerie/commandes/adhesion', j, {
      method: 'POST', body: JSON.stringify({ campagneId, publicCible }),
    }),

  adhererGratuitement: (j: string | null, campagneId: string, publicCible: string) =>
    authed<AdhesionVue>(`/api/adhesions/campagnes/${campagneId}/adherer`, j, {
      method: 'POST', body: JSON.stringify({ publicCible }),
    }),
  // --------------------------------------------------------- trésorerie

  mesCommandes: (j: string | null) =>
    authed<CommandeVue[]>('/api/tresorerie/commandes/moi', j),

  /**
   * Le secret client de l'intention DÉJÀ créée.
   *
   * <p>Relu, jamais refabriqué : créer une intention à chaque appel laisserait
   * chez Stripe une traînée d'intentions toutes payables pour une seule
   * commande.
   */
  secretDeCommande: (j: string | null, commandeId: string) =>
    authed<{ secretClient: string }>(`/api/tresorerie/commandes/${commandeId}/secret`, j),

  annulerCommande: (j: string | null, commandeId: string) =>
    authed<void>(`/api/tresorerie/commandes/${commandeId}/annuler`, j, { method: 'POST' }),

  rembourserCommande: (j: string | null, commandeId: string, motif: string) =>
    authed<void>(`/api/tresorerie/commandes/${commandeId}/rembourser`, j, {
      method: 'POST', body: JSON.stringify({ motif }),
    }),

  journal: (j: string | null, associationId: string) =>
    authed<JournalVue>(`/api/tresorerie/associations/${associationId}/journal`, j),
}

/**
 * Envoie les octets DIRECTEMENT au stockage, avec l'URL présignée.
 *
 * <p>Volontairement hors de `authed` : cette requête ne va pas à notre API et ne
 * doit surtout pas porter le jeton Keycloak — l'envoyer à un hôte tiers (MinIO,
 * S3) le divulguerait. Elle ne porte que les en-têtes que le serveur a signés.
 *
 * <p>C'est aussi pourquoi le fichier ne transite jamais par le backend : il
 * n'aurait rien à en faire, et 15 Mo par affiche à travers un pod Java est
 * exactement le genre de goulot qu'on ne remarque qu'en production.
 */
export async function deposerFichier(depot: DepotVue, fichier: File): Promise<void> {
  if (fichier.size > depot.tailleMaxOctets) {
    throw new ErreurApi(
      413,
      `Fichier trop volumineux : ${Math.round(fichier.size / 1024 / 1024)} Mo, `
        + `maximum ${Math.round(depot.tailleMaxOctets / 1024 / 1024)} Mo.`,
    )
  }
  const reponse = await fetch(depot.url, {
    method: depot.methode,
    headers: depot.enTetes,
    body: fichier,
  })
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, "Le dépôt du fichier a échoué.")
  }
}