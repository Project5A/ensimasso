import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi, type ThemeVue } from '../api'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

/**
 * Les jetons de style qu'un bureau peut régler.
 *
 * Volontairement peu nombreux et nommés par leur RÔLE, pas par leur valeur :
 * une association choisit « la couleur principale », pas « le bleu ». C'est ce
 * qui permet au portail de composer un thème cohérent sans qu'un bureau ait à
 * penser aux contrastes.
 */
const JETONS: { cle: string; libelle: string; defaut: string; aide: string }[] = [
  { cle: 'couleurPrimaire', libelle: 'Couleur principale', defaut: '#8B1E3F',
    aide: 'Titres, boutons, liens' },
  { cle: 'couleurSecondaire', libelle: 'Couleur secondaire', defaut: '#2F4858',
    aide: 'Accents et éléments discrets' },
  { cle: 'couleurFond', libelle: 'Fond', defaut: '#FFFFFF', aide: 'Fond des pages' },
  { cle: 'couleurTexte', libelle: 'Texte', defaut: '#1A1A1A', aide: 'Corps de texte' },
]

function lire(theme: ThemeVue | null): Record<string, string> {
  const valeurs = Object.fromEntries(JETONS.map((j) => [j.cle, j.defaut]))
  if (!theme) return valeurs
  try {
    const stockes = JSON.parse(theme.tokens) as Record<string, unknown>
    for (const j of JETONS) {
      if (typeof stockes[j.cle] === 'string') valeurs[j.cle] = stockes[j.cle] as string
    }
  } catch {
    // Un thème illisible ne doit pas empêcher d'en composer un nouveau.
  }
  return valeurs
}

/**
 * Le thème du mandat.
 *
 * <p>Cet écran manquait, et avec lui toute possibilité pour un bureau de
 * choisir ses couleurs. La table, l'index « une seule version publiée », les
 * transitions du domaine, le clonage à la passation, le rendu par le portail
 * public et la permission THEME_EDITER existaient tous ; il n'y avait ni route
 * ni écran. Un thème publié ne pouvait naître que d'un INSERT à la main, et le
 * brouillon déposé par chaque passation restait à jamais impubliable — donc une
 * association perdait son identité visuelle à chaque changement de bureau.
 */
export default function Theme() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const [theme, setTheme] = useState<ThemeVue | null>(null)
  const [valeurs, setValeurs] = useState<Record<string, string>>(() => lire(null))
  const [occupe, setOccupe] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  const charger = useCallback(async () => {
    try {
      const t = await apiDashboard.theme(jeton(), mandatId)
      setTheme(t)
      setValeurs(lire(t))
    } catch (e) {
      setErreur(e instanceof ErreurApi ? e.message : 'Chargement impossible.')
    }
  }, [jeton, mandatId])

  useEffect(() => { void charger() }, [charger])

  const agir = async (action: () => Promise<unknown>, message: string) => {
    setOccupe(true)
    setErreur(null)
    setInfo(null)
    try {
      await action()
      await charger()
      setInfo(message)
    } catch (e) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible.')
    } finally {
      setOccupe(false)
    }
  }

  const publie = theme?.statut === 'PUBLIEE'

  return (
    <>
      <FilMandat section="Thème" />
      <NavMandat mandatId={mandatId} actif="theme" />

      <h1>Thème du mandat</h1>
      <p className="aide">
        Ces couleurs valent pour <strong>ce mandat</strong> et pour lui seul. Les
        années précédentes gardent les leurs : c'est ce qui rend une archive
        fidèle.
      </p>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}

      <p className="statut">
        {theme
          ? `Version ${theme.numero} — ${publie ? 'publiée' : 'brouillon non publié'}`
          : 'Aucun thème : les valeurs ci-dessous sont celles par défaut.'}
      </p>

      <form onSubmit={(e) => e.preventDefault()}>
        {JETONS.map((j) => (
          <p key={j.cle}>
            <label htmlFor={`jeton-${j.cle}`}>{j.libelle}</label>
            <input
              id={`jeton-${j.cle}`}
              type="color"
              value={valeurs[j.cle]}
              disabled={occupe}
              onChange={(e) => setValeurs({ ...valeurs, [j.cle]: e.target.value })}
            />
            <input
              type="text"
              aria-label={`${j.libelle}, valeur hexadécimale`}
              value={valeurs[j.cle]}
              disabled={occupe}
              onChange={(e) => setValeurs({ ...valeurs, [j.cle]: e.target.value })}
            />
            <small>{j.aide}</small>
          </p>
        ))}

        <div
          className="apercu-theme"
          style={{ background: valeurs.couleurFond, color: valeurs.couleurTexte }}
        >
          <strong style={{ color: valeurs.couleurPrimaire }}>Aperçu</strong>{' '}
          <span style={{ color: valeurs.couleurSecondaire }}>texte secondaire</span>
        </div>

        <button
          type="button"
          disabled={occupe}
          onClick={() =>
            void agir(
              () => apiDashboard.enregistrerTheme(jeton(), mandatId, valeurs),
              'Brouillon enregistré. Il faut le publier pour qu’il soit visible.',
            )
          }
        >
          Enregistrer le brouillon
        </button>

        {/* Publier est une action distincte : enregistrer ne doit jamais
            changer ce que voient les visiteurs sans qu'on l'ait demandé. */}
        <button
          type="button"
          disabled={occupe || publie}
          onClick={() =>
            void agir(() => apiDashboard.publierTheme(jeton(), mandatId), 'Thème publié.')
          }
        >
          Publier
        </button>
      </form>
    </>
  )
}
