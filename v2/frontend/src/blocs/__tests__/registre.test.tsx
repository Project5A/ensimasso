import { describe, expect, it } from 'vitest'
import { RendreBloc } from '../registre'
import type { BlocRendu } from '../../types'

/**
 * Le point le plus fragile du constructeur de pages : le registre des types
 * vit en BASE, le moteur de rendu est un BUNDLE COMPILÉ.
 *
 * <p>Insérer une ligne dans `type_bloc`, ou revenir en arrière sur le front,
 * produit nécessairement un type que ce bundle ne connaît pas. La revue
 * d'architecture avait relevé que deux des trois conceptions proposées
 * présentaient ce catalogue en base comme un pur avantage sans dire ce qui se
 * passe alors. Réponse ici : on ignore proprement, la page reste debout.
 */
const bloc = (type: string, payload: Record<string, unknown> = {}): BlocRendu => ({
  id: 'b1',
  type,
  schemaVersion: 1,
  payload,
  urlsMedias: {},
  equipe: [],
})

describe('registre de blocs', () => {
  it('un type inconnu ne fait pas tomber la page', () => {
    const rendu = RendreBloc({ bloc: bloc('TYPE_DU_FUTUR'), slug: 'bde', anneeCode: '2025-2026' })
    expect(rendu).not.toBeUndefined()
  })

  it('un bloc au registre serveur mais pas encore rendu est ignoré, pas planté', () => {
    for (const type of ['EVENT_LIST', 'PARTNERS', 'COUNTDOWN', 'EMBED']) {
      expect(() =>
        RendreBloc({ bloc: bloc(type), slug: 'bde', anneeCode: '2025-2026' }),
      ).not.toThrow()
    }
  })

  it('un payload vide ne fait pas tomber un type connu', () => {
    // Un bloc écrit sous un schéma plus ancien peut manquer un champ que la
    // version courante exige. Le rendu doit dégrader, pas échouer.
    for (const type of ['HERO', 'RICH_TEXT', 'STATS', 'FAQ', 'GALLERY', 'CTA_ADHESION', 'TEAM_GRID']) {
      expect(() =>
        RendreBloc({ bloc: bloc(type), slug: 'bde', anneeCode: '2025-2026' }),
      ).not.toThrow()
    }
  })

  it('un payload du mauvais type ne fait pas tomber le rendu', () => {
    expect(() =>
      RendreBloc({
        bloc: bloc('STATS', { items: 'pas un tableau' }),
        slug: 'bde',
        anneeCode: '2025-2026',
      }),
    ).not.toThrow()

    expect(() =>
      RendreBloc({
        bloc: bloc('RICH_TEXT', { doc: 42 }),
        slug: 'bde',
        anneeCode: '2025-2026',
      }),
    ).not.toThrow()
  })
})
