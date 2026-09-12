import { useId } from 'react'

/**
 * Génère un formulaire d'édition à partir du JSON Schema d'un type de bloc.
 *
 * <p>C'est ce qui rend vraie la promesse « les associations personnalisent
 * leurs pages sans intervention d'un développeur » — dans sa forme honnête :
 * ajouter un type de bloc, c'est <em>une ligne dans `type_bloc` et un
 * composant de rendu</em>, sans jamais toucher au constructeur. La revue
 * d'architecture insistait pour que ce soit formulé exactement comme ça, sans
 * laisser croire qu'un nouveau type de bloc n'exige aucun développement.
 *
 * <p>Le schéma servi par l'API est le MÊME que celui qui valide côté serveur :
 * le formulaire ne peut donc pas diverger de ce que la base accepte.
 */

type Schema = {
  type?: string
  enum?: string[]
  minLength?: number
  maxLength?: number
  minimum?: number
  maximum?: number
  minItems?: number
  maxItems?: number
  description?: string
  properties?: Record<string, Schema>
  required?: string[]
  items?: Schema
}

type Props = {
  schema: Schema
  valeur: Record<string, unknown>
  onChange: (v: Record<string, unknown>) => void
}

const humain = (cle: string) =>
  cle.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())

export function ChampsSchema({ schema, valeur, onChange }: Props) {
  const proprietes = schema.properties ?? {}
  const requis = new Set(schema.required ?? [])

  return (
    <div className="champs">
      {Object.entries(proprietes).map(([cle, sousSchema]) => (
        <Champ
          key={cle}
          nom={cle}
          schema={sousSchema}
          requis={requis.has(cle)}
          valeur={valeur[cle]}
          onChange={(v) => onChange({ ...valeur, [cle]: v })}
        />
      ))}
    </div>
  )
}

function Champ({
  nom, schema, requis, valeur, onChange,
}: {
  nom: string; schema: Schema; requis: boolean
  valeur: unknown; onChange: (v: unknown) => void
}) {
  const id = useId()
  const libelle = humain(nom)

  // Le document structuré du texte riche : une zone de saisie simple, dont
  // chaque ligne devient un paragraphe. On ne stocke jamais de HTML, donc il
  // n'y a aucun balisage à injecter et aucun assainisseur à maintenir.
  if (nom === 'doc') {
    const doc = valeur as { content?: { content?: { text?: string }[] }[] } | undefined
    const texte = (doc?.content ?? [])
      .map((p) => (p.content ?? []).map((f) => f.text ?? '').join(''))
      .join('\n\n')

    return (
      <div className="champ">
        <label htmlFor={id}>Texte{requis && <Requis />}</label>
        <textarea
          id={id}
          rows={6}
          value={texte}
          onChange={(e) =>
            onChange({
              type: 'doc',
              content: e.target.value
                .split(/\n{2,}/)
                .filter((p) => p.trim())
                .map((p) => ({ type: 'paragraph', content: [{ type: 'text', text: p.trim() }] })),
            })
          }
        />
        <p className="aide">Une ligne vide sépare deux paragraphes.</p>
      </div>
    )
  }

  if (schema.enum) {
    return (
      <div className="champ">
        <label htmlFor={id}>{libelle}{requis && <Requis />}</label>
        <select id={id} value={String(valeur ?? '')} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {schema.enum.map((o) => (
            <option key={o} value={o}>{o}</option>
          ))}
        </select>
        {schema.description && <p className="aide">{schema.description}</p>}
      </div>
    )
  }

  if (schema.type === 'integer' || schema.type === 'number') {
    return (
      <div className="champ">
        <label htmlFor={id}>{libelle}{requis && <Requis />}</label>
        <input
          id={id}
          type="number"
          min={schema.minimum}
          max={schema.maximum}
          value={valeur === undefined || valeur === null ? '' : String(valeur)}
          onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
        />
      </div>
    )
  }

  if (schema.type === 'boolean') {
    return (
      <div className="champ champ--case">
        <input id={id} type="checkbox" checked={Boolean(valeur)}
               onChange={(e) => onChange(e.target.checked)} />
        <label htmlFor={id}>{libelle}</label>
      </div>
    )
  }

  if (schema.type === 'array') {
    return <ChampListe nom={libelle} schema={schema} valeur={valeur} onChange={onChange} />
  }

  if (schema.type === 'string') {
    const long = (schema.maxLength ?? 0) > 200
    return (
      <div className="champ">
        <label htmlFor={id}>{libelle}{requis && <Requis />}</label>
        {long ? (
          <textarea id={id} rows={3} maxLength={schema.maxLength}
                    value={String(valeur ?? '')} onChange={(e) => onChange(e.target.value || undefined)} />
        ) : (
          <input id={id} type="text" maxLength={schema.maxLength}
                 value={String(valeur ?? '')} onChange={(e) => onChange(e.target.value || undefined)} />
        )}
        {schema.description && <p className="aide">{schema.description}</p>}
      </div>
    )
  }

  // Type non pris en charge par ce générateur : on ne bloque pas l'édition,
  // on tombe sur une saisie JSON brute plutôt que de masquer le champ.
  return (
    <div className="champ">
      <label htmlFor={id}>{libelle} <span className="aide">(JSON)</span></label>
      <textarea
        id={id}
        rows={3}
        defaultValue={JSON.stringify(valeur ?? null, null, 2)}
        onBlur={(e) => {
          try { onChange(JSON.parse(e.target.value)) } catch { /* saisie invalide : ignorée */ }
        }}
      />
    </div>
  )
}

function ChampListe({
  nom, schema, valeur, onChange,
}: { nom: string; schema: Schema; valeur: unknown; onChange: (v: unknown) => void }) {
  const items = Array.isArray(valeur) ? valeur : []
  const itemSchema = schema.items ?? { type: 'string' }
  const max = schema.maxItems ?? 50

  const maj = (i: number, v: unknown) =>
    onChange(items.map((e, j) => (i === j ? v : e)))

  return (
    <fieldset className="champ champ--liste">
      <legend>{nom}</legend>

      {items.map((item, i) => (
        <div className="item" key={i}>
          {itemSchema.type === 'object' ? (
            <ChampsSchema
              schema={itemSchema}
              valeur={(item ?? {}) as Record<string, unknown>}
              onChange={(v) => maj(i, v)}
            />
          ) : (
            <input
              type="text"
              value={String(item ?? '')}
              onChange={(e) => maj(i, e.target.value)}
              aria-label={`${nom} ${i + 1}`}
            />
          )}
          <button type="button" className="lien-danger"
                  onClick={() => onChange(items.filter((_, j) => j !== i))}>
            Retirer
          </button>
        </div>
      ))}

      {items.length < max && (
        <button type="button" className="bouton bouton--petit"
                onClick={() => onChange([...items, itemSchema.type === 'object' ? {} : ''])}>
          Ajouter
        </button>
      )}
    </fieldset>
  )
}

const Requis = () => <span aria-hidden="true" className="requis"> *</span>
