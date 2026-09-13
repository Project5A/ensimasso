import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RendreBloc } from '../registre'
import type { BlocRendu } from '../../types'

afterEach(cleanup)

/**
 * Le point le plus fragile du constructeur de pages : le registre des types
 * vit en BASE, le moteur de rendu est un BUNDLE COMPILÉ. Insérer une ligne
 * dans `type_bloc`, ou revenir en arrière sur le front, produit nécessairement
 * un type que ce bundle ne connaît pas. On ignore proprement, la page reste
 * debout.
 *
 * Ces cas appelaient `RendreBloc(...)` comme une fonction ordinaire et
 * vérifiaient que l'appel ne lève pas. Mais `RendreBloc` n'est qu'un `switch`
 * qui RETOURNE un élément : `<Hero bloc={…} />` décrit un rendu, il ne
 * l'exécute pas. Le corps des composants n'était jamais évalué. Vérifié en
 * faisant exploser `Stats` à chaque rendu : les quatre cas restaient verts.
 *
 * Ils passent donc par `render`, qui monte pour de bon.
 */
const bloc = (type: string, payload: Record<string, unknown> = {}): BlocRendu => ({
  id: 'b1',
  type,
  schemaVersion: 1,
  payload,
  urlsMedias: {},
  equipe: [],
  agenda: [],
  partenaires: [],
})

const contexte = { slug: 'bde', anneeCode: '2025-2026', estCourant: true }

const monter = (b: BlocRendu) => render(<>{RendreBloc({ bloc: b, ...contexte })}</>)

/** Les onze types du registre serveur. Divergence = bloc proposé puis ignoré. */
const TYPES = [
  'HERO', 'RICH_TEXT', 'TEAM_GRID', 'EVENT_LIST', 'GALLERY', 'PARTNERS',
  'STATS', 'FAQ', 'CTA_ADHESION', 'EMBED', 'COUNTDOWN',
] as const

/** Payloads de départ du registre : ceux avec lesquels un bloc est créé. */
const DEPART: Record<string, Record<string, unknown>> = {
  HERO: { titre: 'Titre de la bannière' },
  RICH_TEXT: {
    doc: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Votre texte.' }] }] },
  },
  TEAM_GRID: { source: 'MANDAT_DE_LA_PAGE' },
  EVENT_LIST: { style: 'CARTES', filtre: 'A_VENIR' },
  GALLERY: { mediaKeys: [], disposition: 'GRILLE' },
  PARTNERS: { titre: 'Nos partenaires' },
  STATS: { items: [{ libelle: 'Adhérents', valeur: '0' }] },
  FAQ: { items: [{ question: 'Votre question ?', reponse: 'Votre réponse.' }] },
  CTA_ADHESION: { titre: 'Rejoignez-nous' },
  EMBED: { fournisseur: 'YOUTUBE', ref: 'identifiant-a-remplacer' },
  COUNTDOWN: { titre: 'Compte à rebours', cibleLe: '2030-06-01T20:00:00Z' },
}

describe('registre de blocs', () => {
  it('un type inconnu ne rend RIEN, et prévient le développeur', () => {
    const avertir = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      monter(bloc('TYPE_DU_FUTUR'))

      // Rien sur la page : c'est le choix assumé du registre — un bundle en
      // retard sur la base ignore proprement plutôt que d'afficher une erreur
      // à un visiteur qui n'y peut rien.
      expect(document.body.textContent).toBe('')
      // Mais le silence s'arrête au visiteur : en développement, celui qui
      // vient d'ajouter le type au registre doit l'apprendre tout de suite.
      expect(avertir).toHaveBeenCalledWith(
        expect.stringContaining('TYPE_DU_FUTUR'),
      )
    } finally {
      avertir.mockRestore()
    }
  })

  it.each(TYPES)('%s se monte avec son payload de départ', (type) => {
    expect(() => monter(bloc(type, DEPART[type]))).not.toThrow()
  })

  it.each(TYPES)('%s se monte avec un payload VIDE', (type) => {
    // Un bloc écrit sous un schéma plus ancien peut manquer un champ que la
    // version courante exige. Le rendu doit dégrader, pas échouer.
    expect(() => monter(bloc(type))).not.toThrow()
  })

  it.each([
    ['STATS', { items: 'pas un tableau' }],
    ['RICH_TEXT', { doc: 42 }],
    ['FAQ', { items: [{ question: 12, reponse: null }] }],
    ['GALLERY', { mediaKeys: 'pas un tableau' }],
    ['HERO', { titre: [], actions: 'pas un tableau' }],
    ['COUNTDOWN', { titre: 'X', cibleLe: 'pas une date' }],
    ['EMBED', { fournisseur: 'INCONNU', ref: null }],
  ])('%s survit à un payload du mauvais type', (type, payload) => {
    expect(() => monter(bloc(type, payload as Record<string, unknown>))).not.toThrow()
  })

  it('le contenu réellement rendu vient bien du payload', () => {
    // Sans cette assertion, tout ce qui précède resterait vrai d'un moteur de
    // rendu qui ne rendrait rien du tout.
    monter(bloc('HERO', { titre: 'Bienvenue au BDE' }))
    expect(screen.getByText('Bienvenue au BDE')).toBeTruthy()
  })
})
