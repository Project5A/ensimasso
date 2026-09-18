import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Countdown, Embed, EventList, Partners, Stats, periode, resteJusqua } from '../Blocs'
import type { BlocRendu, EvenementVue, PartenaireVue } from '../../types'

// Vitest n'active pas les globales, donc le nettoyage automatique de
// Testing Library ne s'installe pas : sans ceci, le DOM du test précédent
// reste en place et une assertion « cet élément a disparu » passe ou échoue
// pour de mauvaises raisons.
afterEach(cleanup)

const bloc = (
  type: string,
  payload: Record<string, unknown> = {},
  extra: Partial<BlocRendu> = {},
): BlocRendu => ({
  id: 'b1',
  type,
  schemaVersion: 1,
  payload,
  urlsMedias: {},
  equipe: [],
  agenda: [],
  partenaires: [],
  ...extra,
})

const evenement = (p: Partial<EvenementVue> = {}): EvenementVue => ({
  slug: 'gala',
  titre: 'Gala de printemps',
  resume: null,
  lieu: 'Palais des Congrès',
  debutLe: '2026-10-03T18:00:00Z',
  finLe: null,
  statut: 'PUBLIE',
  complet: false,
  motifAnnulation: null,
  lien: null,
  afficheUrl: null,
  ...p,
})

describe('titre de l’agenda', () => {
  // Le titre ne suivait que `estCourant` : sur une page VIVANTE dont le bloc
  // est réglé sur PASSES, le bureau demandait « ce qu'on a organisé », le
  // serveur rendait bien des évènements passés — et le titre annonçait « À
  // venir » au-dessus. Ces cas sont le miroir du ternaire serveur de
  // SelectionBlocs.agenda.
  const rendre = (payload: Record<string, unknown>, courant: boolean) =>
    render(
      <EventList
        bloc={bloc('EVENT_LIST', payload, { agenda: [evenement()] })}
        anneeCode="2025-2026"
        estCourant={courant}
      />,
    )

  it('page vivante, filtre par défaut : « À venir »', () => {
    rendre({}, true)
    expect(screen.getByRole('heading', { name: 'À venir' })).toBeDefined()
  })

  it('page vivante, filtre PASSES : ce n’est plus « À venir »', () => {
    rendre({ filtre: 'PASSES' }, true)
    expect(screen.queryByRole('heading', { name: 'À venir' })).toBeNull()
    expect(screen.getByRole('heading', { name: 'Ce qui a déjà eu lieu' })).toBeDefined()
  })

  it('page vivante, filtre TOUS : le titre nomme l’année, pas un temps', () => {
    rendre({ filtre: 'TOUS' }, true)
    expect(screen.getByRole('heading', { name: 'Les évènements de 2025-2026' })).toBeDefined()
  })

  it('archive : le filtre du bloc est ignoré, comme côté serveur', () => {
    // Sur une archive, SelectionBlocs force « TOUS » quel que soit le bloc.
    rendre({ filtre: 'A_VENIR' }, false)
    expect(screen.queryByRole('heading', { name: 'À venir' })).toBeNull()
    expect(screen.getByRole('heading', { name: 'Les évènements de 2025-2026' })).toBeDefined()
  })
})

describe('Stats', () => {
  it('le terme précède sa définition dans le DOM', () => {
    render(<Stats bloc={bloc('STATS', { items: [{ libelle: 'Adhérents', valeur: '420' }] })} />)

    // <dd> était écrit AVANT <dt> : HTML invalide, et un lecteur d'écran
    // apparie le terme et la définition dans l'ordre du document. L'ordre
    // visuel — le grand nombre d'abord — est rétabli par la feuille de style.
    const groupe = screen.getByText('Adhérents').parentElement
    const enfants = [...(groupe?.children ?? [])].map((e) => e.tagName)
    expect(enfants).toEqual(['DT', 'DD'])
    expect(screen.getByText('420').tagName).toBe('DD')
  })
})

describe('EventList', () => {
  it('affiche les évènements fournis par le serveur', () => {
    render(
      <EventList
        bloc={bloc('EVENT_LIST', {}, { agenda: [evenement()] })}
        anneeCode="2025-2026"
        estCourant
      />,
    )
    expect(screen.getByRole('heading', { name: 'À venir' })).toBeDefined()
    expect(screen.getByText('Gala de printemps')).toBeDefined()
    expect(screen.getByText('Palais des Congrès')).toBeDefined()
  })

  it("sur une archive, le titre dit que c'est le bilan du mandat", () => {
    render(
      <EventList
        bloc={bloc('EVENT_LIST', {}, { agenda: [evenement()] })}
        anneeCode="2024-2025"
        estCourant={false}
      />,
    )
    // « À venir » sur une page d'archive serait un contresens : le mandat est
    // terminé, rien de ce qui s'y trouve n'est à venir.
    expect(screen.getByRole('heading', { name: 'Les évènements de 2024-2025' })).toBeDefined()
    expect(screen.queryByRole('heading', { name: 'À venir' })).toBeNull()
  })

  it('un évènement annulé est affiché avec son motif, et sans lien de billetterie', () => {
    render(
      <EventList
        bloc={bloc(
          'EVENT_LIST',
          {},
          {
            agenda: [
              evenement({
                statut: 'ANNULE',
                motifAnnulation: 'Amphi repris par l’administration.',
                lien: 'https://billetterie.example.org/gala',
              }),
            ],
          },
        )}
        anneeCode="2025-2026"
        estCourant
      />,
    )
    expect(screen.getByText('Annulé')).toBeDefined()
    expect(screen.getByText('Amphi repris par l’administration.')).toBeDefined()
    // Continuer à vendre des places pour une soirée annulée serait pire que de
    // ne rien afficher.
    expect(screen.queryByRole('link', { name: 'Billetterie' })).toBeNull()
  })

  it("« complet » n'est affiché que pour un évènement qui a bien lieu", () => {
    const { rerender } = render(
      <EventList
        bloc={bloc('EVENT_LIST', {}, { agenda: [evenement({ complet: true })] })}
        anneeCode="2025-2026"
        estCourant
      />,
    )
    expect(screen.getByText('Complet')).toBeDefined()

    rerender(
      <EventList
        bloc={bloc(
          'EVENT_LIST',
          {},
          { agenda: [evenement({ complet: true, statut: 'ANNULE' })] },
        )}
        anneeCode="2025-2026"
        estCourant
      />,
    )
    expect(screen.queryByText('Complet')).toBeNull()
  })

  it('un agenda vide le dit, plutôt que de disparaître', () => {
    render(<EventList bloc={bloc('EVENT_LIST')} anneeCode="2025-2026" estCourant />)
    expect(screen.getByText(/Aucun évènement annoncé/)).toBeDefined()
  })

  it('une date illisible ne fait pas tomber le bloc', () => {
    expect(() =>
      render(
        <EventList
          bloc={bloc('EVENT_LIST', {}, { agenda: [evenement({ debutLe: 'pas-une-date' })] })}
          anneeCode="2025-2026"
          estCourant
        />,
      ),
    ).not.toThrow()
  })
})

describe('mise en forme des dates', () => {
  it("écrit l'année, sans quoi une page d'archive ne dit pas de quand elle parle", () => {
    expect(periode(new Date('2024-11-23T20:00:00Z'), null)).toContain('2024')
  })

  it('sur plusieurs jours de la même année, l’année n’est écrite qu’une fois', () => {
    const texte = periode(new Date('2024-12-06T08:00:00Z'), new Date('2024-12-07T08:00:00Z'))
    expect(texte).toMatch(/^du /)
    expect(texte.match(/2024/g)).toHaveLength(1)
  })

  it('une soirée qui finit à 2 h reste une soirée, pas deux jours', () => {
    const texte = periode(new Date('2026-10-03T20:00:00Z'), new Date('2026-10-04T02:00:00Z'))
    expect(texte).not.toMatch(/^du /)
    expect(texte).toContain('3 octobre 2026')
  })

  it('deux vraies journées restent deux journées', () => {
    const texte = periode(new Date('2026-11-07T09:00:00Z'), new Date('2026-11-09T17:00:00Z'))
    expect(texte).toMatch(/^du /)
  })

  it('le réveillon est une soirée, même en changeant d’année', () => {
    const texte = periode(new Date('2026-12-31T20:00:00Z'), new Date('2027-01-01T04:00:00Z'))
    expect(texte).not.toMatch(/^du /)
    expect(texte).toContain('31 décembre 2026')
  })

  it('un séjour à cheval sur deux années écrit les deux', () => {
    const texte = periode(new Date('2026-12-28T09:00:00Z'), new Date('2027-01-03T17:00:00Z'))
    expect(texte).toContain('2026')
    expect(texte).toContain('2027')
  })
})

describe('Partners', () => {
  const pa = (nom: string, niveau: PartenaireVue['niveau']): PartenaireVue => ({
    nom,
    niveau,
    url: null,
    logoUrl: null,
  })

  it('groupe par niveau, dans l’ordre servi par l’API', () => {
    render(
      <Partners
        bloc={bloc(
          'PARTNERS',
          { titre: 'Ils nous soutiennent' },
          { partenaires: [pa('Métropole', 'OR'), pa('MMArena', 'ARGENT')] },
        )}
      />,
    )
    expect(screen.getByRole('heading', { name: 'Ils nous soutiennent' })).toBeDefined()
    expect(screen.getByText('Partenaires principaux')).toBeDefined()
    expect(screen.getByText('Métropole')).toBeDefined()
  })

  it('un lien de partenaire ne donne pas accès à notre onglet', () => {
    render(
      <Partners
        bloc={bloc(
          'PARTNERS',
          {},
          { partenaires: [{ ...pa('Métropole', 'OR'), url: 'https://exemple.fr' }] },
        )}
      />,
    )
    const lien = screen.getByRole('link')
    expect(lien.getAttribute('rel')).toContain('noopener')
    expect(lien.getAttribute('target')).toBe('_blank')
  })

  it('sans partenaire, le bloc ne rend rien du tout', () => {
    const { container } = render(<Partners bloc={bloc('PARTNERS')} />)
    expect(container.innerHTML).toBe('')
  })
})

describe('Embed', () => {
  it('fabrique l’URL depuis la liste blanche, jamais depuis le payload', () => {
    const { container } = render(
      <Embed bloc={bloc('EMBED', { fournisseur: 'YOUTUBE', ref: 'dQw4w9WgXcQ' })} />,
    )
    const iframe = container.querySelector('iframe')
    expect(iframe?.getAttribute('src')).toBe(
      'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ',
    )
  })

  it('une référence Spotify devient un chemin, pas une chaîne brute', () => {
    const { container } = render(
      <Embed bloc={bloc('EMBED', { fournisseur: 'SPOTIFY', ref: 'track:4cOdK2wGLETKBW3PvgPWqT' })} />,
    )
    expect(container.querySelector('iframe')?.getAttribute('src')).toBe(
      'https://open.spotify.com/embed/track/4cOdK2wGLETKBW3PvgPWqT',
    )
  })

  it('un fournisseur hors liste blanche ne rend rien', () => {
    const { container } = render(
      <Embed bloc={bloc('EMBED', { fournisseur: 'IFRAME', ref: 'https://mechant.example/x' })} />,
    )
    expect(container.innerHTML).toBe('')
  })

  it('une référence qui ne rentre pas dans le moule est refusée', () => {
    // Le serveur valide déjà `ref`, mais un bloc écrit sous un schéma plus
    // ancien passerait sous ce contrôle. Une iframe dont l'URL vient du contenu
    // est un trou par lequel on sert ce qu'on veut depuis notre origine.
    for (const ref of ['../../evil', 'a', 'https://mechant.example', 'x'.repeat(50)]) {
      const { container } = render(
        <Embed bloc={bloc('EMBED', { fournisseur: 'YOUTUBE', ref })} />,
      )
      expect(container.innerHTML).toBe('')
    }
  })
})

describe('Countdown', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  const CIBLE = '2026-12-25T00:00:00Z'

  it('calcule le reste en jours, heures, minutes et secondes', () => {
    const reste = resteJusqua(
      new Date('2026-12-25T00:00:00Z'),
      new Date('2026-12-23T21:58:30Z'),
    )
    expect(reste).toEqual({ jours: 1, heures: 2, minutes: 1, secondes: 30 })
  })

  it('rend null une fois la cible atteinte', () => {
    expect(resteJusqua(new Date('2026-01-01T00:00:00Z'), new Date('2026-01-01T00:00:00Z'))).toBeNull()
  })

  it('affiche le cadran avant la date', () => {
    vi.setSystemTime(new Date('2026-12-24T00:00:00Z'))
    render(<Countdown bloc={bloc('COUNTDOWN', { titre: 'Noël', cibleLe: CIBLE })} />)
    expect(screen.getByRole('heading', { name: 'Noël' })).toBeDefined()
    expect(screen.getByText('jour')).toBeDefined()
  })

  it('après la date, affiche le message de fin plutôt que « 0 jour » indéfiniment', () => {
    vi.setSystemTime(new Date('2027-01-05T00:00:00Z'))
    render(
      <Countdown
        bloc={bloc('COUNTDOWN', { titre: 'Noël', cibleLe: CIBLE, messageApres: 'C’est passé !' })}
      />,
    )
    expect(screen.getByText('C’est passé !')).toBeDefined()
  })

  it('sans message de fin, le bloc périmé disparaît au lieu de mentir', () => {
    vi.setSystemTime(new Date('2027-01-05T00:00:00Z'))
    const { container } = render(
      <Countdown bloc={bloc('COUNTDOWN', { titre: 'Noël', cibleLe: CIBLE })} />,
    )
    expect(container.innerHTML).toBe('')
  })

  it('une cible illisible ne rend rien', () => {
    const { container } = render(
      <Countdown bloc={bloc('COUNTDOWN', { titre: 'X', cibleLe: 'demain' })} />,
    )
    expect(container.innerHTML).toBe('')
  })
})
