/**
 * Miroir des enregistrements renvoyés par le portail public.
 *
 * <p>Écrits à la main et non générés : la prochaine étape prévue est de les
 * dériver d'un schéma OpenAPI produit en CI, pour que « le front appelle une
 * route qui n'existe pas » devienne une erreur de compilation. Dans la v1, le
 * tableau de bord appelait trois endpoints inexistants — PUT /api/events/{id},
 * DELETE /api/events/{id} et DELETE /api/posts/{id} — sans que rien ne le dise.
 */

export type AssociationVue = {
  slug: string
  nom: string
  type: 'BUREAU' | 'CLUB' | 'TECHNIQUE'
}

export type MandatVue = {
  anneeCode: string
  statut: 'PREPARATION' | 'EN_FONCTION' | 'CLOS'
  estCourant: boolean
}

export type MembreVue = {
  poste: string
  titreAffiche: string
  ordre: number
  photoUrl: string | null
}

export type PageLien = {
  slug: string
  titre: string
  ordreMenu: number
}

/**
 * Un évènement de l'agenda, tel que le portail le renvoie.
 *
 * <p>`statut` peut valoir ANNULE : un évènement annulé reste affiché, barré,
 * avec son motif. Le faire disparaître laisserait sans réponse ceux qui
 * comptaient venir.
 */
export type EvenementVue = {
  slug: string
  titre: string
  resume: string | null
  lieu: string | null
  debutLe: string
  finLe: string | null
  statut: 'PUBLIE' | 'ANNULE'
  complet: boolean
  motifAnnulation: string | null
  lien: string | null
  afficheUrl: string | null
}

export type NiveauPartenaire = 'OR' | 'ARGENT' | 'BRONZE' | 'SOUTIEN'

export type PartenaireVue = {
  nom: string
  niveau: NiveauPartenaire
  url: string | null
  logoUrl: string | null
}

/** Le payload d'un bloc est volontairement opaque : chaque composant valide le sien. */
export type BlocRendu = {
  id: string
  type: string
  schemaVersion: number
  payload: Record<string, unknown>
  urlsMedias: Record<string, string>
  equipe: MembreVue[]
  agenda: EvenementVue[]
  partenaires: PartenaireVue[]
}

export type Theme = {
  accent?: string
  accentContraste?: string
  encre?: string
  fond?: string
  police?: 'SANS' | 'SERIF'
  rayon?: string
}

export type PageRendue = {
  association: AssociationVue
  mandat: MandatVue
  slug: string
  titre: string
  versionNumero: number
  publieLe: string | null
  theme: Theme
  blocs: BlocRendu[]
  menu: PageLien[]
  anneesDisponibles: string[]
  /** L'année du mandat EN FONCTION, s'il y en a un. `anneesDisponibles` ne dit
   *  pas lequel est lequel : sans cela, l'année en cours se retrouvait listée
   *  parmi les « années précédentes » et liée par l'URL d'archive. */
  anneeCourante: string | null
}
