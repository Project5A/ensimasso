import { useEffect, useRef, useState } from 'react'
import { apiDashboard, ErreurApi, type MediaVue } from '../api'
import { useAuth } from '../auth/AuthContext'

/**
 * Choisir une image de la médiathèque, depuis n'importe quel formulaire.
 *
 * <p>C'était le maillon manquant. `evenement.mediaKey` et
 * `partenaire.logoMediaKey` traversent toute la chaîne — colonne, entité,
 * service, API, portail public — et aucun écran ne permettait d'en choisir un.
 * Les deux formulaires envoyaient donc `null`, ce qui, les services
 * REMPLAÇANT tout, effaçait l'image à chaque modification.
 *
 * <p>Ce composant ne montre que les médias DISPONIBLES : un dépôt inachevé ou
 * refusé ne doit pas pouvoir être choisi, sinon la page publiée pointerait vers
 * un objet absent du stockage — et une page publiée est immuable.
 */
export function SelecteurMedia({
  associationId,
  anneeCode,
  valeur,
  onChange,
  libelle,
  id,
}: {
  associationId: string | null
  anneeCode: string | null
  valeur: string | null
  onChange: (cle: string | null) => void
  libelle: string
  id: string
}) {
  const { jeton } = useAuth()
  const [medias, setMedias] = useState<MediaVue[] | null>(null)
  const [urls, setUrls] = useState<Record<string, string>>({})
  const [erreur, setErreur] = useState<string | null>(null)

  // `jeton` dans une référence, pas dans les dépendances : son identité peut
  // changer à chaque rendu, et cet effet écrit des objets neufs dans l'état.
  const refJeton = useRef(jeton)
  refJeton.current = jeton

  useEffect(() => {
    if (!associationId || !anneeCode) return
    let vivant = true
    apiDashboard
      .medias(refJeton.current(), associationId, anneeCode)
      .then(async (liste) => {
        const utilisables = liste.filter((m) => m.statut === 'DISPONIBLE')
        const resolues = await apiDashboard.urlsMedias(
          refJeton.current(), utilisables.map((m) => m.cle))
        if (!vivant) return
        setMedias(utilisables)
        setUrls(resolues)
      })
      .catch((e: unknown) => {
        if (vivant) {
          setErreur(e instanceof ErreurApi ? e.message : 'Médiathèque indisponible')
        }
      })
    return () => { vivant = false }
  }, [associationId, anneeCode])

  // La valeur déjà enregistrée peut désigner un média retiré depuis, ou d'une
  // autre année. On le DIT plutôt que de faire disparaître la clé du
  // formulaire : l'effacer en silence serait reproduire le défaut qu'on corrige.
  const orpheline = valeur != null && medias != null && !medias.some((m) => m.cle === valeur)

  return (
    <div className="selecteur-media">
      <label htmlFor={id}>{libelle}</label>

      {erreur && <p role="alert" className="erreur">{erreur}</p>}

      <select
        id={id}
        value={valeur ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
      >
        <option value="">Aucune</option>
        {orpheline && (
          <option value={valeur}>{valeur} (hors médiathèque de cette année)</option>
        )}
        {medias?.map((m) => (
          <option key={m.id} value={m.cle}>
            {m.cle}{m.texteAlternatif ? ` — ${m.texteAlternatif}` : ''}
          </option>
        ))}
      </select>

      {valeur && urls[valeur] && (
        <img className="selecteur-media__apercu" src={urls[valeur]} alt="" />
      )}

      {medias?.length === 0 && (
        <p className="aide">
          Aucune image disponible : déposez-en une dans la médiathèque.
        </p>
      )}
    </div>
  )
}
