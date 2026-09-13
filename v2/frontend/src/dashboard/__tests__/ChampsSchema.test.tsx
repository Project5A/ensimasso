import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ChampsSchema } from '../ChampsSchema'

/**
 * Le formulaire tel que l'éditeur s'en sert : la valeur saisie REVIENT dans
 * la propriété `valeur`.
 *
 * <p>C'est la seule façon d'éprouver un composant contrôlé, et c'est ce qui
 * manquait ici : tous les cas passaient un `onChange` factice, donc `valeur`
 * ne changeait jamais, donc la boucle rendu → saisie → rendu n'était jamais
 * parcourue. Le champ de texte riche y perdait chaque espace de fin et chaque
 * saut de paragraphe ; aucun cas ne pouvait s'en apercevoir.
 */
function Pilote({ schema, initial = {} }: {
  schema: Parameters<typeof ChampsSchema>[0]['schema']
  initial?: Record<string, unknown>
}) {
  const [valeur, setValeur] = useState<Record<string, unknown>>(initial)
  return <ChampsSchema schema={schema} valeur={valeur} onChange={setValeur} />
}

const SCHEMA_DOC = {
  type: 'object', required: ['doc'], properties: { doc: { type: 'object' } },
}

const paragraphes = (zone: HTMLElement) =>
  (zone as HTMLTextAreaElement).value.split(/\n{2,}/).filter((p) => p.trim())

/**
 * Le générateur de formulaire lit le JSON Schema servi par l'API — le MÊME
 * que celui qui valide côté serveur. Ces tests utilisent les schémas réels
 * de la migration V4, pour que le formulaire ne puisse pas diverger de ce que
 * la base accepte.
 */
describe('ChampsSchema', () => {
  afterEach(cleanup)

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

  describe('texte riche, tel qu on le tape', () => {
    it('deux mots séparés par une espace restent deux mots', async () => {
      render(<Pilote schema={SCHEMA_DOC} />)
      const zone = screen.getByLabelText('Texte *') as HTMLTextAreaElement

      await userEvent.type(zone, 'Bonjour monde')

      // Donnait « Bonjourmonde » : l'espace finale était retirée par le trim
      // avant d'avoir été suivie d'une lettre. Écrire une phrase entière était
      // impossible.
      expect(zone.value).toBe('Bonjour monde')
    })

    it('une ligne vide ouvre bien un second paragraphe', async () => {
      render(<Pilote schema={SCHEMA_DOC} />)
      const zone = screen.getByLabelText('Texte *') as HTMLTextAreaElement

      await userEvent.type(zone, 'Premier{Enter}{Enter}Second')

      // Le premier saut de ligne, seul, ne survivait pas jusqu'au second :
      // le champ n'a jamais pu contenir plus d'un paragraphe.
      expect(zone.value).toBe('Premier\n\nSecond')
      expect(paragraphes(zone)).toEqual(['Premier', 'Second'])
    })

    it('un document existant est relu tel quel', () => {
      render(<Pilote schema={SCHEMA_DOC} initial={{
        doc: { type: 'doc', content: [
          { type: 'paragraph', content: [{ type: 'text', text: 'Un' }] },
          { type: 'paragraph', content: [{ type: 'text', text: 'Deux' }] },
        ] },
      }} />)

      expect((screen.getByLabelText('Texte *') as HTMLTextAreaElement).value)
        .toBe('Un\n\nDeux')
    })

    it('ce qui part au serveur reste normalisé', async () => {
      const recu: unknown[] = []
      function Espion() {
        const [valeur, setValeur] = useState<Record<string, unknown>>({})
        return (
          <ChampsSchema schema={SCHEMA_DOC} valeur={valeur}
                        onChange={(v) => { recu.push(v.doc); setValeur(v) }} />
        )
      }
      render(<Espion />)

      await userEvent.type(screen.getByLabelText('Texte *'), '  Titre  {Enter}{Enter}  Suite  ')

      // La zone garde ce qu'on tape ; le document, lui, n'a aucune raison de
      // transporter des espaces de bord ou des paragraphes vides.
      expect(recu[recu.length - 1]).toEqual({
        type: 'doc',
        content: [
          { type: 'paragraph', content: [{ type: 'text', text: 'Titre' }] },
          { type: 'paragraph', content: [{ type: 'text', text: 'Suite' }] },
        ],
      })
    })
  })
})
