# ENSIMAsso

Plateforme des associations étudiantes de l'ENSIM.

Le dépôt contient **deux versions** du projet :

| Dossier | Quoi | État |
|---|---|---|
| [`v2/`](v2/) | Réécriture en cours | Développement actif — [README](v2/README.md) |
| `BackEnd/` · `FrontEnd/` | Version d'origine (2025) | Figée, conservée pour référence |

---

## v2 — la réécriture

Tout est décrit dans **[`v2/README.md`](v2/README.md)**. Pour démarrer :

```bash
cd v2
make            # liste les cibles
make test       # tests unitaires et d'architecture, sans Docker
make up         # infrastructure de développement (Docker requis)
```

L'idée qui structure la réécriture : une **association** est permanente, un
**mandat** est le terme d'un bureau. Les pages, le thème, l'agenda, les
partenaires et l'équipe appartiennent au mandat, pas à l'association. C'est ce
qui fait exister les archives par année sans code particulier, et ce qu'un
modèle clé sur `(association, année)` ne sait pas représenter — les élections
de BDE ont lieu au printemps, pas au 1er septembre.

---

## v1 — la version d'origine

Écrite en 2025 par les auteurs ci-dessous. Elle a été auditée avant la
réécriture ; les constats sont repris dans `v2/README.md` et chacun est
aujourd'hui vérifié par un test.

**Ce README-ci décrivait autre chose que le projet.** Il annonçait MySQL
— c'était Azure SQL Server — Jenkins pour l'intégration continue, qui n'a
jamais existé, et un `docker-compose up` sans fichier compose. Les chemins
d'installation (`frontend/ensimasso`, `backend`) ne correspondaient pas non
plus à ceux du dépôt (`FrontEnd/ensimasso`, `BackEnd`). C'est le constat OPS-04
de l'audit, et il est corrigé ici parce qu'un document qui décrit un autre
système que le sien fait perdre plus de temps qu'il n'en fait gagner.

Stack réelle de la v1 : React 18 (Create React App) · Spring Boot 3 · Azure SQL
Server · Azure Blob Storage · Stripe. Pas de conteneurisation fonctionnelle,
pas de chaîne d'intégration, pas de tests.

> ⚠️ L'historique git de la v1 contient des identifiants de production réels
> (base de données, stockage, clé Stripe), committés en février 2025. Ils
> doivent être considérés comme compromis et révoqués, que le code tourne
> encore ou non — retirer un secret d'un fichier ne le retire pas de
> l'historique.

Elle se lance encore, pour qui veut la voir tourner :

```bash
cd BackEnd   && ./mvnw spring-boot:run     # exige des identifiants Azure valides
cd FrontEnd/ensimasso && npm install && npm start
```

---

## Auteurs

- ELYACOUBI Yahya
- ELKALCHY Achraf
- BOUIBER Taha

## Licence

MIT — voir [`LICENSE`](LICENSE).
