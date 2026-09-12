import { Route, Routes } from 'react-router-dom'
import { FournisseurAuth } from '../auth/AuthContext'
import { Protege } from '../auth/Protege'
import Editeur from './Editeur'
import Pages from './Pages'
import Tableau from './Tableau'

/**
 * L'espace authentifié, chargé à la demande.
 *
 * <p>Le fournisseur OIDC vit ICI et non à la racine de l'application : sans
 * cela, `oidc-client-ts` entre dans le chunk d'entrée et chaque visiteur d'une
 * page publique télécharge 68 Ko de bibliothèque d'authentification qu'il
 * n'utilisera jamais. C'est précisément le reproche fait à la v1, qui servait
 * three.js et un modèle 3-D de 2,9 Mo à tout le monde.
 */
export default function Espace() {
  return (
    <FournisseurAuth>
      <Routes>
        {/* Monté à la fois sur /connexion/retour et /tableau/* : les chemins
            sont donc relatifs au point de montage. */}
        <Route path="/" element={<Protege><Tableau /></Protege>} />
        <Route path="mandats/:mandatId" element={<Protege><Pages /></Protege>} />
        <Route path="pages/:pageId" element={<Protege><Editeur /></Protege>} />
      </Routes>
    </FournisseurAuth>
  )
}
