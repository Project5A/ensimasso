import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

/**
 * Garde de route.
 *
 * <p>Il <em>rend</em> l'invite de connexion à la place du contenu, il ne
 * « redirige plus tard ». La v1 faisait, dans le corps du rendu du tableau de
 * bord, un `setTimeout(() => navigate('/'), 3000)` sans `return` : le tableau
 * de bord d'un visiteur non connecté s'affichait entièrement pendant trois
 * secondes avant de disparaître.
 *
 * <p>Et ce n'est qu'un confort d'interface : la véritable autorisation est
 * côté serveur, dans PolitiqueAcces. Un garde client ne protège rien.
 */
export function Protege({ children }: { children: ReactNode }) {
  const { utilisateur, chargement, connecter } = useAuth()

  if (chargement) {
    return (
      <main className="page page--centree">
        <p aria-live="polite">Vérification de la session…</p>
      </main>
    )
  }

  if (!utilisateur) {
    return (
      <main className="page page--centree">
        <h1>Connexion requise</h1>
        <p>Le tableau de bord est réservé aux membres des bureaux d'association.</p>
        <button className="bouton" onClick={() => void connecter()}>
          Se connecter
        </button>
      </main>
    )
  }

  return <>{children}</>
}
