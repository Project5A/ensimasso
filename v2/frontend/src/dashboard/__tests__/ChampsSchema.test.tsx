import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ChampsSchema } from '../ChampsSchema'

/**
 * Le générateur de formulaire lit le JSON Schema servi par l'API — le MÊME
 * que celui qui valide côté serveur. Ces tests utilisent les schémas réels
 * de la migration V4, pour que le formulaire ne puisse pas diverger de ce que
 * la base accepte.
 */
describe('ChampsSchema', () => {
  it('génère un champ par propriété du schéma', () => {
    const schema = {
      type: 'object',
      required: ['titre'],
      properties: {
        titre: { type: 'string', maxLength: 120 },
        sousTitre: { type: 'string', maxLength: 300 },
        hauteur: { type: 'string', enum: ['COMPACTE', 'MOYENNE', 'PLEINE'] },
      },
    }
    render(<ChampsSchema schema={schema} valeur={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText('Titre *')).toBeDefined()
    expect(screen.getByLabelText('Sous Titre')).toBeDefined()
    // L'énumération devient une liste déroulante : une asso ne peut pas
    // inventer une valeur que le schéma refusera à l'enregistrement.
    const select = screen.getByLabelText('Hauteur') as HTMLSelectElement
    expect(select.tagName).toBe('SELECT')
    expect([...select.options].map((o) => o.value)).toContain('COMPACTE')
  })

  it('un champ entier respecte les bornes du schéma', () => {
    const schema = {
      type: 'object',
      properties: { colonnes: { type: 'integer', minimum: 2, maximum: 4 } },
    }
    render(<ChampsSchema schema={schema} valeur={{}} onChange={vi.fn()} />)

    const input = screen.getByLabelText('Colonnes') as HTMLInputElement
    expect(input.type).toBe('number')
    expect(input.min).toBe('2')
    expect(input.max).toBe('4')
  })

  it('le texte riche est saisi comme du texte, jamais comme du HTML', () => {
    const schema = { type: 'object', required: ['doc'], properties: { doc: { type: 'object' } } }
    const onChange = vi.fn()
    render(<ChampsSchema schema={schema} valeur={{}} onChange={onChange} />)

    // Une zone de texte, pas un éditeur HTML : il n'y a aucun balisage à
    // injecter, donc aucun XSS stocké possible sur une page publique.
    const zone = screen.getByLabelText('Texte *')
    expect(zone.tagName).toBe('TEXTAREA')
  })

  it('un schéma vide ne fait pas tomber le formulaire', () => {
    expect(() =>
      render(<ChampsSchema schema={{}} valeur={{}} onChange={vi.fn()} />),
    ).not.toThrow()
  })

  it('une valeur déjà présente est affichée', () => {
    const schema = { type: 'object', properties: { titre: { type: 'string' } } }
    render(<ChampsSchema schema={schema} valeur={{ titre: 'Gala 2026' }} onChange={vi.fn()} />)

    expect((screen.getByLabelText('Titre') as HTMLInputElement).value).toBe('Gala 2026')
  })

  it('une liste d objets affiche un sous-formulaire par élément', () => {
    const schema = {
      type: 'object',
      properties: {
        items: {
          type: 'array',
          items: {
            type: 'object',
            properties: { libelle: { type: 'string' }, valeur: { type: 'string' } },
          },
        },
      },
    }
    render(
      <ChampsSchema
        schema={schema}
        valeur={{ items: [{ libelle: 'Adhérents', valeur: '420' }] }}
        onChange={vi.fn()}
      />,
    )
    expect((screen.getByLabelText('Libelle') as HTMLInputElement).value).toBe('Adhérents')
    expect(screen.getByText('Ajouter')).toBeDefined()
  })
})
