# Portail public — ENSIMAsso

Rendu public des pages d'association. React 19-ready, Vite, TypeScript strict.

```bash
npm install
VITE_API_PROXY=http://localhost:8080 npm run dev   # http://localhost:5173
npm run build && npm run preview
npm test
```

## Ce qui est délibéré

**Une seule base d'URL d'API**, venue de la configuration (`src/api.ts`). La v1
en avait quatre en parallèle — une instance axios, les valeurs par défaut
globales modifiées par effet de bord à l'import, des `fetch()` avec l'URL Azure
en dur dans huit fichiers, et des `fetch()` relatifs qui ne marchaient nulle
part. L'URL du backend y était recopiée huit fois.

**Le développement passe par un proxy**, comme la production passe par Traefik :
l'API est sur la même origine que le front. CORS n'existe donc pas comme
problème. La v1 avait une liste d'origines figée à `http://localhost:3000`, ce
qui rendait le front déployé incapable d'appeler le back déployé.

**Le thème vient des données.** Les jetons du mandat deviennent des variables
CSS (`src/theme.ts`) : chaque association a une page différente sans une ligne
de code spécifique, et une archive garde l'apparence de son année.

**Un type de bloc inconnu est ignoré, pas fatal.** Le registre vit en base, le
moteur de rendu est un bundle compilé : un type inconnu est inévitable, et
`src/blocs/__tests__/registre.test.tsx` vérifie que la page reste debout.

**Le texte riche est un document structuré, jamais du HTML.** Aucun
`dangerouslySetInnerHTML`, donc aucun XSS stocké possible sur une page publique
partagée par dix associations.

**Découpage par route.** La v1 n'avait pas un seul `React.lazy` : chaque
visiteur téléchargeait toutes les pages, plus three.js et un modèle 3-D de
2,9 Mo.

## Reste à faire

- [ ] Tableau de bord : constructeur de pages (glisser-déposer, aperçu, publication)
- [ ] Connexion Keycloak (OIDC + PKCE, client `ensimasso-dashboard` déjà déclaré)
- [ ] Blocs `EVENT_LIST`, `PARTNERS`, `COUNTDOWN`, `EMBED` — au registre serveur, pas encore rendus
- [ ] Types dérivés d'un schéma OpenAPI en CI, pour qu'appeler une route
      inexistante devienne une erreur de compilation
- [ ] Rendu serveur pour le référencement des pages publiques
