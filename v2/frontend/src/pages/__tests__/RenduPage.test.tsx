import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { RenduPage } from '../RenduPage'
import type { BlocRendu, PageRendue } from '../../types'

afterEach(cleanup)

const bloc = (type: string, payload: Record<string, unknown>): BlocRendu => ({
  id: `b-${type}`, type, schemaVersion: 1, payload,
  urlsMedias: {}, equipe: [], agenda: [], partenaires: [],
})

const page = (extra: Partial<PageRendue> = {}): PageRendue => ({
  association: { slug: 'bde', nom: 'Bureau des Élèves', type: 'BUREAU' },
  mandat: { anneeCode: '2025-2026', statut: 'EN_FONCTION', estCourant: true },
  slug: 'accueil',
  titre: 'Accueil',
  versionNumero: 4,
  publieLe: null,
  theme: { accent: '#1f4e79' },
  blocs: [bloc('HERO', { titre: 'Bureau des Élèves' }), bloc('FAQ', { items: [] })],
  menu: [
    { slug: 'accueil', titre: 'Accueil', ordreMenu: 0 },
    { slug: 'equipe', titre: "L'équipe", ordreMenu: 1 },
  ],
  anneesDisponibles: ['2025-2026', '2024-2025'],
  ...extra,
})

function monter(p: PageRendue, apercu = false) {
  return render(<MemoryRouter><RenduPage page={p} apercu={apercu} /></MemoryRouter>)
}

describe('RenduPage', () => {
  it("l'aperçu rend exactement les mêmes blocs que la page publique", () => {
    // La propriété qui compte : un aperçu qui passe par un rendu parallèle
    // finit par diverger, et il diverge en silence.
    const { container: publique } = monter(page())
    const blocsPublics = publique.querySelectorAll('main > *').length
    cleanup()

    const { container: apercu } = monter(page(), true)
    expect(apercu.querySelectorAll('main > *')).toHaveLength(blocsPublics)
    expect(screen.getByRole('heading', { name: 'Bureau des Élèves' })).toBeDefined()
  })

  it('le thème du mandat devient des variables CSS, en aperçu comme en public', () => {
    const { container } = monter(page({ theme: { accent: '#5B4B8A' } }), true)
    const racine = container.querySelector('.asso') as HTMLElement
    expect(racine.style.getPropertyValue('--accent')).toBe('#5B4B8A')
  })

  it("en aperçu, le menu n'est pas cliquable", () => {
    // Chaque autre page a son propre brouillon : ces liens mèneraient vers ce
    // qui est publié, donc vers autre chose que ce qu'on relit.
    monter(page(), true)
    expect(screen.queryByRole('link', { name: "L'équipe" })).toBeNull()
    expect(screen.getByText("L'équipe")).toBeDefined()
  })

  it('en public, le menu est bien cliquable', () => {
    monter(page())
    expect(screen.getByRole('link', { name: "L'équipe" })).toBeDefined()
  })

  it("le bandeau d'archive ne s'affiche pas par-dessus un aperçu", () => {
    // Un brouillon de mandat clos est un aperçu, pas une archive : afficher
    // « vous consultez l'archive » inviterait à cliquer vers la page publique.
    const close = page({ mandat: { anneeCode: '2024-2025', statut: 'CLOS', estCourant: false } })
    monter(close, true)
    expect(screen.queryByText(/Vous consultez l'archive/)).toBeNull()

    cleanup()
    monter(close)
    expect(screen.getByText(/Vous consultez l'archive/)).toBeDefined()
  })

  it('le numéro de version rendu est celui de la page servie', () => {
    monter(page({ versionNumero: 12 }), true)
    expect(screen.getByText(/version 12/)).toBeDefined()
  })
})
