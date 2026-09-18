import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiDashboard,
  deposerFichier,
  ErreurApi,
  TYPES_MEDIA_ACCEPTES,
  type MediaVue,
} from '../api'
import { useAuth } from '../auth/AuthContext'
import { FilMandat, NavMandat } from './NavMandat'

const LIBELLE_STATUT: Record<MediaVue['statut'], string> = {
  ATTENTE_DEPOT: 'Dépôt inachevé',
  DISPONIBLE: 'Disponible',
  REJETE: 'Refusé à la vérification',
  SUPPRIME: 'Retiré',
}

function poids(octets: number | null): string {
  if (octets == null) return ''
  return octets >= 1024 * 1024
    ? `${(octets / 1024 / 1024).toFixed(1)} Mo`
    : `${Math.max(1, Math.round(octets / 1024))} Ko`
}

/**
 * La médiathèque d'une association, pour une année.
 *
 * <p>Le module `media` du serveur était complet — dépôt présigné, vérification
 * de la signature réelle du fichier, texte alternatif, retrait, isolation par
 * association — et aucun écran ne l'appelait. Ses six routes n'étaient
 * atteignables qu'avec `curl`, si bien que `evenement.mediaKey` et
 * `partenaire.logoMediaKey`, tous deux RENDUS PAR LE PORTAIL PUBLIC, ne
 * pouvaient être renseignés que par un INSERT à la main, et que
 * `texte_alternatif` n'était jamais écrit.
 *
 * <p>Le dépôt se fait en deux temps, et les octets ne passent jamais par notre
 * backend : on demande une URL présignée, on envoie le fichier au stockage,
 * puis on demande au serveur de CONFIRMER — c'est là qu'il relit la taille et
 * le type réels dans le stockage, plutôt que de croire ce que le navigateur a
 * annoncé.
 */
export default function Mediatheque() {
  const { mandatId = '' } = useParams()
  const { jeton } = useAuth()

  const [contexte, setContexte] = useState<{ associationId: string; anneeCode: string } | null>(null)
  const [medias, setMedias] = useState<MediaVue[] | null>(null)
  const [urls, setUrls] = useState<Record<string, string>>({})
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  // `jeton` passe par une référence et non par les dépendances des effets :
  // useAuth rend une fonction dont l'identité peut changer à chaque rendu, et
  // ces effets écrivent des objets NEUFS dans l'état — ils se rappelleraient
  // donc eux-mêmes sans fin. Même précaution que dans Editeur.tsx.
  const refJeton = useRef(jeton)
  refJeton.current = jeton

  // L'écran reçoit un mandat ; la médiathèque est rangée par association et par
  // année. Les postes de l'utilisateur portent déjà les deux, on les y lit
  // plutôt que d'ajouter une route pour la même information.
  useEffect(() => {
    apiDashboard
      .mesPostes(refJeton.current())
      .then((postes) => {
        const p = postes.find((x) => x.mandatId === mandatId)
        if (!p) {
          setErreur("Ce mandat n'est pas le vôtre.")
          return
        }
        setContexte({ associationId: p.associationId, anneeCode: p.anneeCode })
      })
      .catch((e: unknown) =>
        setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [mandatId])

  const charger = useCallback(async () => {
    if (!contexte) return
    const liste = await apiDashboard.medias(
      refJeton.current(), contexte.associationId, contexte.anneeCode)
    setMedias(liste)
    // Les URL de lecture sont signées et expirent : on les redemande à chaque
    // chargement plutôt que de les garder.
    const visibles = liste.filter((m) => m.statut === 'DISPONIBLE').map((m) => m.cle)
    setUrls(await apiDashboard.urlsMedias(refJeton.current(), visibles))
  }, [contexte])

  useEffect(() => {
    if (!contexte) return
    charger().catch((e: unknown) =>
      setErreur(e instanceof ErreurApi ? e.message : 'Service indisponible'))
  }, [contexte, charger])

  async function agir(action: () => Promise<unknown>, message?: string) {
    if (enCours) return
    setEnCours(true)
    setErreur(null)
    setInfo(null)
    try {
      await action()
      await charger()
      if (message) setInfo(message)
    } catch (e: unknown) {
      setErreur(e instanceof ErreurApi ? e.message : 'Opération impossible')
    } finally {
      setEnCours(false)
    }
  }

  async function deposer(fichier: File) {
    if (!contexte) return
    await agir(async () => {
      const depot = await apiDashboard.preparerDepot(
        jeton(), contexte.associationId, fichier.name, fichier.type)
      await deposerFichier(depot, fichier)
      // Le serveur relit les octets réels : un SVG déposé sous image/png est
      // refusé ici, et pas au moment où une page publique l'affiche.
      await apiDashboard.confirmerDepot(jeton(), depot.mediaId)
    }, `« ${fichier.name} » déposé.`)
  }

  const accepte = useMemo(() => TYPES_MEDIA_ACCEPTES.join(','), [])

  return (
    <main className="page">
      <FilMandat section="Médiathèque" />
      <NavMandat mandatId={mandatId} actif="medias" />

      <h1>Médiathèque</h1>
      <p className="aide">
        Les images et documents de votre association pour l'année
        {contexte ? ` ${contexte.anneeCode}` : ''}. Ce sont eux que l'on choisit
        pour l'affiche d'un évènement ou le logo d'un partenaire.
      </p>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}
      {info && <p role="status" className="info">{info}</p>}

      <div className="formulaire-ligne">
        <label htmlFor="media-fichier">Déposer un fichier</label>
        <input
          id="media-fichier"
          type="file"
          accept={accepte}
          disabled={enCours || !contexte}
          onChange={(e) => {
            const fichier = e.target.files?.[0]
            // Le champ est remis à zéro : sans cela, redéposer le même fichier
            // après un refus ne déclenche aucun évènement.
            e.target.value = ''
            if (fichier) void deposer(fichier)
          }}
        />
        <p className="aide">
          JPEG, PNG, WebP, AVIF, GIF ou PDF, 15 Mo au plus. Le type annoncé est
          vérifié contre les octets réels.
        </p>
      </div>

      {!medias && !erreur && <p aria-live="polite">Chargement…</p>}

      {medias?.length === 0 && (
        <div className="vide-etat">
          <h2>Aucun média</h2>
          <p>Déposez une première image : elle sera utilisable partout ensuite.</p>
        </div>
      )}

      {medias && medias.length > 0 && (
        <ul className="mediatheque">
          {medias.map((m) => (
            <li key={m.id} className="mediatheque__item">
              <div className="mediatheque__vignette">
                {urls[m.cle] && m.contentType.startsWith('image/') ? (
                  <img src={urls[m.cle]} alt={m.texteAlternatif ?? ''} loading="lazy" />
                ) : (
                  <span className="mediatheque__sansimage">{m.contentType}</span>
                )}
              </div>

              <div className="mediatheque__detail">
                <code>{m.cle}</code>
                <span className="mediatheque__statut">
                  {LIBELLE_STATUT[m.statut]}{m.tailleOctets ? ` — ${poids(m.tailleOctets)}` : ''}
                </span>

                {m.statut === 'DISPONIBLE' && (
                  <form
                    className="mediatheque__alt"
                    onSubmit={(e) => {
                      e.preventDefault()
                      const champ = new FormData(e.currentTarget).get('alt')
                      void agir(
                        () => apiDashboard.decrireMedia(jeton(), m.id, String(champ ?? '')),
                        'Texte alternatif enregistré.')
                    }}
                  >
                    <label htmlFor={`alt-${m.id}`}>Texte alternatif</label>
                    <input
                      id={`alt-${m.id}`}
                      name="alt"
                      type="text"
                      maxLength={500}
                      defaultValue={m.texteAlternatif ?? ''}
                      placeholder="Ce que montre l'image, pour qui ne la voit pas"
                    />
                    <button className="bouton" type="submit" disabled={enCours}>
                      Enregistrer
                    </button>
                  </form>
                )}

                {m.statut !== 'SUPPRIME' && (
                  <button
                    className="lien lien-danger"
                    type="button"
                    disabled={enCours}
                    onClick={() => void agir(
                      () => apiDashboard.supprimerMedia(jeton(), m.id), 'Média retiré.')}
                  >
                    Retirer
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
