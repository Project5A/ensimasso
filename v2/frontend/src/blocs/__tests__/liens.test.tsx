import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RendreBloc } from '../registre'
import type { BlocRendu } from '../../types'

/**
 * Les liens que le constructeur de pages laisse écrire.
 *
 * Le registre de blocs a délibérément supprimé l'injection de HTML : RICH_TEXT
 * stocke un document structuré, jamais une chaîne de balisage, parce que « JSON
 * Schema ne sait pas exprimer : cette chaîne est du HTML sûr ». Le raisonnement
 * est juste. Il n'a simplement jamais été porté jusqu'aux champs d'URL, qui
 * arrivent pourtant dans un `href` sans le moindre contrôle.
 */
const bloc = (type: string, payload: Record<string, unknown>): BlocRendu => ({
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

const aucunLienExecutable = () => {
  const dangereux = Array.from(document.querySelectorAll('a')).filter((a) =>
    /^\s*(javascript|data|vbscript):/i.test(a.getAttribute('href') ?? ''),
  )
  expect(dangereux.map((a) => a.getAttribute('href'))).toEqual([])
}

describe('liens fournis par le payload', () => {
  it("une action HERO ne peut pas porter d'URL exécutable", () => {
    render(
      <>
        {RendreBloc({
          bloc: bloc('HERO', {
            titre: 'Gala',
            actions: [
              { libelle: 'Réserver', href: 'javascript:alert(document.cookie)' },
              { libelle: 'Programme', href: '/assos/bde/programme' },
            ],
          }),
          ...contexte,
        })}
      </>,
    )

    aucunLienExecutable()
    // Le lien hostile disparaît entièrement : un bouton qui ne mène nulle part
    // vaut moins que pas de bouton. Les liens légitimes du même bloc restent.
    expect(screen.queryByText('Réserver')).toBeNull()
    expect(screen.getByText('Programme').closest('a')!.getAttribute('href'))
      .toBe('/assos/bde/programme')
  })

  it.each([
    ['javascript:alert(1)', 'schéma exécutable'],
    ['JaVaScRiPt:alert(1)', 'casse mélangée'],
    ['  javascript:alert(1)', 'espaces de tête'],
    ['java\tscript:alert(1)', 'tabulation au milieu du schéma'],
    ['java\nscript:alert(1)', 'retour à la ligne au milieu du schéma'],
    ['data:text/html,<script>alert(1)</script>', 'document embarqué'],
    ['vbscript:msgbox(1)', 'autre schéma exécutable'],
  ])('refuse %s (%s)', (href) => {
    render(
      <>
        {RendreBloc({
          bloc: bloc('HERO', { titre: 'Gala', actions: [{ libelle: 'Piège', href }] }),
          ...contexte,
        })}
      </>,
    )
    aucunLienExecutable()
    expect(screen.queryByText('Piège')).toBeNull()
  })

  it("une action COUNTDOWN ne peut pas porter d'URL exécutable", () => {
    render(
      <>
        {RendreBloc({
          bloc: bloc('COUNTDOWN', {
            titre: 'J-7',
            cibleLe: '2030-01-01T00:00:00Z',
            action: { libelle: "J'y vais", href: 'javascript:alert(1)' },
          }),
          ...contexte,
        })}
      </>,
    )

    // Le compte à rebours lui-même doit bien s'être affiché, sans quoi ce cas
    // ne vérifierait rien : c'est ce qui est arrivé la première fois, avec un
    // nom de champ erroné.
    expect(screen.getByText('J-7')).toBeTruthy()
    aucunLienExecutable()
    expect(screen.queryByText("J'y vais")).toBeNull()
  })

  it('une URL http normale reste intacte', () => {
    render(
      <>
        {RendreBloc({
          bloc: bloc('HERO', {
            titre: 'Gala',
            actions: [{ libelle: 'Billetterie', href: 'https://billetterie.example/gala' }],
          }),
          ...contexte,
        })}
      </>,
    )

    expect(screen.getByText('Billetterie').closest('a')!.getAttribute('href'))
      .toBe('https://billetterie.example/gala')
  })

  it('un lien de billetterie et un lien de partenaire sont filtrés eux aussi', () => {
    render(
      <>
        {RendreBloc({
          bloc: {
            ...bloc('EVENT_LIST', { titre: 'Agenda', source: 'MANDAT_DE_LA_PAGE' }),
            agenda: [{
              id: 'e1', titre: 'Gala', debutLe: '2030-06-01T20:00:00Z', finLe: null,
              lieu: 'Le Mans', lien: 'javascript:alert(1)', annule: false,
              motifAnnulation: null, mediaKey: null,
            }],
          } as never,
          ...contexte,
        })}
        {RendreBloc({
          bloc: {
            ...bloc('PARTNERS', { titre: 'Partenaires', source: 'MANDAT_DE_LA_PAGE' }),
            partenaires: [{ nom: 'Sponsor', url: 'javascript:alert(1)', mediaKey: null, niveau: 'OR' }],
          } as never,
          ...contexte,
        })}
      </>,
    )

    // Sans ces deux assertions, le cas serait passé au vert avec des noms de
    // types erronés — c'est exactement ce qui s'est produit au premier essai :
    // aucun des deux blocs ne s'affichait, et « aucun lien exécutable » était
    // trivialement vrai.
    expect(screen.getAllByText('Gala').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Sponsor').length).toBeGreaterThan(0)
    aucunLienExecutable()
  })
})
