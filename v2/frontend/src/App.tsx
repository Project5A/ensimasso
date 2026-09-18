import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'

// Découpage par route. La v1 n'avait pas un seul React.lazy : chaque visiteur
// téléchargeait toutes les pages, plus three.js et un modèle 3-D de 2,9 Mo.
const Annuaire = lazy(() => import('./pages/Annuaire'))
const PageAsso = lazy(() => import('./pages/PageAsso'))
// Tout l'espace authentifié — fournisseur OIDC compris — derrière une seule
// frontière paresseuse, pour qu'aucun visiteur public ne le télécharge.
const Espace = lazy(() => import('./dashboard/Espace'))

// Les pages d'adhésion, chargées à la demande elles aussi : elles emportent le
// fournisseur OIDC, et une page publique ne doit pas le télécharger.
const PageAdherer = lazy(() =>
  import('./adhesion/pages').then((m) => ({ default: m.PageAdherer })))
const PageMesCommandes = lazy(() =>
  import('./adhesion/pages').then((m) => ({ default: m.PageMesCommandes })))
const PagePaiement = lazy(() =>
  import('./adhesion/pages').then((m) => ({ default: m.PagePaiement })))

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
        {/* Avant /assos/:slug/:pageSlug : le bloc CTA_ADHESION du portail
            public pointe vers /assos/{slug}/adherer depuis toujours, et cette
            route n'existait pas. Elle tombait donc sur la recherche d'une page
            nommée « adherer » — le seul bouton d'adhésion du site public ne
            menait nulle part. */}
        <Route path="/assos/:slug/adherer" element={<PageAdherer />} />
        <Route path="/assos/:slug" element={<PageAsso />} />
        <Route path="/assos/:slug/:pageSlug" element={<PageAsso />} />

        {/* L'archive : mêmes composants, autre mandat. */}
        <Route path="/assos/:slug/:annee/:pageSlug" element={<PageAsso archive />} />

        {/* Tableau de bord. Le garde REND l'invite de connexion à la place du
            contenu — il ne l'affiche pas puis ne redirige trois secondes plus
            tard, comme le faisait la v1. La vraie autorisation reste serveur. */}
        <Route path="/commandes" element={<PageMesCommandes />} />
        <Route path="/commandes/:commandeId" element={<PagePaiement />} />
        <Route path="/connexion/retour" element={<Espace />} />
        <Route path="/tableau/*" element={<Espace />} />

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
