import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiDashboard, ErreurApi, type PageVue } from '../api'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

/** Les pages d'un mandat, et la création d'une nouvelle. */
export default function Pages() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()
  const navigate = useNavigate()

  const [pages, setPages] = useState<PageVue[] | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const [titre, setTitre] = useState('')
  const [enCours, setEnCours] = useState(false)

  const charger = useCallback(() => {
    apiDashboard
      .pages(jeton(), mandatId)
      .then(setPages)
      .catch((e: unknown) => setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [jeton, mandatId])

  useEffect(() => { charger() }, [charger])

  async function creer(e: React.FormEvent) {
    e.preventDefault()
    if (!titre.trim() || enCours) return
    setEnCours(true)
    setErreur(null)
    try {
      // Le slug est dérivé du titre : sans accents, sans espaces. Le serveur
      // le valide de toute façon avec le même motif.
      const slug = titre.trim().toLowerCase()
        .normalize('NFD').replace(/[̀-ͯ]/g, '')
        .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60)

      const page = await apiDashboard.creerPage(jeton(), mandatId, {
        slug, titre: titre.trim(), ordreMenu: pages?.length ?? 0,
      })
      setTitre('')
      navigate(`/tableau/pages/${page.id}`)
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Création impossible')
    } finally {
      setEnCours(false)
    }
  }

  return (
    <main className="page page--centree">
      <FilMandat section="Pages" />
      <h1>Pages du mandat</h1>
      <NavMandat mandatId={mandatId} actif="pages" />

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {!pages && !erreur && <p aria-live="polite">Chargement…</p>}

      {pages && (
        <ul className="liste-pages">
          {pages.map((p) => (
            <li key={p.id}>
              <Link to={`/tableau/pages/${p.id}`}>
                <span>{p.titre}</span>
                <code>/{p.slug}</code>
              </Link>
            </li>
          ))}
          {pages.length === 0 && <li className="vide">Aucune page pour l'instant.</li>}
        </ul>
      )}

      <form onSubmit={creer} className="formulaire-ligne">
        <label htmlFor="titre-page">Nouvelle page</label>
        <div>
          <input id="titre-page" type="text" value={titre} maxLength={200}
                 placeholder="Nos évènements" onChange={(e) => setTitre(e.target.value)} />
          <button className="bouton" type="submit" disabled={!titre.trim() || enCours}>
            {enCours ? 'Création…' : 'Créer'}
          </button>
        </div>
      </form>
    </main>
  )
}
