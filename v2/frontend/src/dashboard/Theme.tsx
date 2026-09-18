import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi, type ThemeVue } from '../api'
import type { Theme } from '../types'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

/**
 * Les jetons de style qu'un bureau peut régler.
 *
 * Volontairement peu nombreux et nommés par leur RÔLE, pas par leur valeur :
 * une association choisit « la couleur principale », pas « le bleu ». C'est ce
 * qui permet au portail de composer un thème cohérent sans qu'un bureau ait à
 * penser aux contrastes.
 *
 * <p><strong>Les clés sont celles que le portail LIT.</strong> Cet écran
 * écrivait `couleurPrimaire`, `couleurSecondaire`, `couleurFond` et
 * `couleurTexte` ; `variablesDuTheme`, dans src/theme.ts, lit `accent`,
 * `accentContraste`, `encre`, `fond`, `rayon` et `police`. Aucune des quatre
 * n'était donc jamais lue par personne : un bureau choisissait ses couleurs,
 * enregistrait, publiait — et sa page publique retombait sur les valeurs par
 * défaut. Pire, le payload REMPLACE le précédent : un thème posé autrement
 * (clonage à la passation, INSERT) était détruit par le premier enregistrement.
 * Les deux vocabulaires sont maintenant le même, et
 * {@code Theme.contrat.test.tsx} vérifie qu'ils le restent.
 */
type Genre = 'couleur' | 'choix'

const JETONS: {
  cle: keyof ThemeJetons; genre: Genre; libelle: string; defaut: string; aide: string
  options?: [string, string][]
}[] = [
  { cle: 'accent', genre: 'couleur', libelle: 'Couleur principale', defaut: '#8B1E3F',
    aide: 'Titres, boutons, liens' },
  { cle: 'accentContraste', genre: 'couleur', libelle: 'Texte sur la couleur principale',
    defaut: '#FFFFFF', aide: 'Doit rester lisible posé sur la couleur principale' },
  { cle: 'encre', genre: 'couleur', libelle: 'Texte', defaut: '#101820',
    aide: 'Corps de texte' },
  { cle: 'fond', genre: 'couleur', libelle: 'Fond', defaut: '#F6F7F9',
    aide: 'Fond des pages' },
  { cle: 'rayon', genre: 'choix', libelle: 'Arrondi des angles', defaut: '8px',
    aide: 'Boutons, cartes, images',
    options: [['0px', 'Angles droits'], ['4px', 'Léger'], ['8px', 'Arrondi'],
              ['16px', 'Très arrondi']] },
  { cle: 'police', genre: 'choix', libelle: 'Police', defaut: 'SANS',
    aide: 'Un choix parmi deux : le portail ne charge aucune police distante',
    options: [['SANS', 'Sans empattement'], ['SERIF', 'Avec empattements']] },
]

/** Exactement les jetons que `variablesDuTheme` sait lire — ni plus, ni moins. */
type ThemeJetons = Required<Theme>

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

  // Le jeton est lu à l'appel, pas capturé dans les dépendances.
  //
  // `jeton` change d'identité à chaque renouvellement silencieux du jeton OIDC
  // — `automaticSilentRenew` est actif et `addUserLoaded` remplace
  // l'utilisateur toutes les quelques minutes. Tant qu'il figurait dans les
  // dépendances de `charger`, l'effet de chargement se rejouait tout seul et
  // `setValeurs(lire(t))` écrasait les couleurs que le bureau était en train de
  // choisir par celles enregistrées. Du travail perdu, sans un mot, sans une
  // action de qui que ce soit.
  //
  // Recharger APRÈS une action reste juste : ce que le serveur a enregistré
  // est alors la vérité, et c'est bien elle qu'il faut réafficher.
  const refJeton = useRef(jeton)
  useEffect(() => { refJeton.current = jeton }, [jeton])

  const charger = useCallback(async () => {
    try {
      const t = await apiDashboard.theme(refJeton.current(), mandatId)
      setTheme(t)
      setValeurs(lire(t))
    } catch (e) {
      setErreur(e instanceof ErreurApi ? e.message : 'Chargement impossible.')
    }
  }, [mandatId])

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
            {j.genre === 'couleur' ? (
              <>
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
              </>
            ) : (
              // Une liste fermée, pas une saisie libre : ce sont les deux seuls
              // jetons qui ne sont pas des couleurs, et rien ne gagnerait à ce
              // qu'un bureau puisse y écrire n'importe quoi.
              <select
                id={`jeton-${j.cle}`}
                value={valeurs[j.cle]}
                disabled={occupe}
                onChange={(e) => setValeurs({ ...valeurs, [j.cle]: e.target.value })}
              >
                {(j.options ?? []).map(([valeur, libelle]) => (
                  <option key={valeur} value={valeur}>{libelle}</option>
                ))}
              </select>
            )}
            <small>{j.aide}</small>
          </p>
        ))}

        <div
          className="apercu-theme"
          style={{ background: valeurs.fond, color: valeurs.encre }}
        >
          <strong style={{ color: valeurs.accent }}>Aperçu</strong>{' '}
          <span style={{ background: valeurs.accent, color: valeurs.accentContraste }}>
            texte sur la couleur principale
          </span>
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
