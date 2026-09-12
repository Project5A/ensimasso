import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'

// Découpage par route. La v1 n'avait pas un seul React.lazy : chaque visiteur
// téléchargeait toutes les pages, plus three.js et un modèle 3-D de 2,9 Mo.
const Annuaire = lazy(() => import('./pages/Annuaire'))
const PageAsso = lazy(() => import('./pages/PageAsso'))

function Chargement() {
  return (
    <main className="page page--centree">
      <p aria-live="polite">Chargement…</p>
    </main>
  )
}

export default function App() {
  return (
    <Suspense fallback={<Chargement />}>
      <Routes>
        <Route path="/" element={<Annuaire />} />

        {/* Les routes sont paramétrées. Dans la v1, la page d'association était
            une unique route littérale /assos/bdlc pointant sur un composant de
            1 117 lignes avec l'identifiant d'association 5 codé en dur — chaque
            nouvelle asso aurait exigé une copie du fichier. */}
        <Route path="/assos/:slug" element={<PageAsso />} />
        <Route path="/assos/:slug/:pageSlug" element={<PageAsso />} />

        {/* L'archive : mêmes composants, autre mandat. */}
        <Route path="/assos/:slug/:annee/:pageSlug" element={<PageAsso archive />} />

        <Route
          path="*"
          element={
            <main className="page page--centree">
              <h1>Page introuvable</h1>
              <p><a href="/">Retour à l'annuaire</a></p>
            </main>
          }
        />
      </Routes>
    </Suspense>
  )
}
