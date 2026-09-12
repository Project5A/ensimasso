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
  return (await reponse.json()) as T
}

export const api = {
  annuaire: () => get<AssociationVue[]>('/api/public/associations'),

  page: (slugAsso: string, slugPage = 'accueil') =>
    get<PageRendue>(`/api/public/associations/${slugAsso}/pages/${slugPage}`),

  /** Une archive est une autre année, pas un autre code. */
  archive: (slugAsso: string, annee: string, slugPage = 'accueil') =>
    get<PageRendue>(`/api/public/associations/${slugAsso}/annees/${annee}/pages/${slugPage}`),
}
