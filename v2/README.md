# ENSIMAsso v2 — plateforme des associations étudiantes

Réécriture de la plateforme des associations de l'ENSIM, en monolithe modulaire
auto-hébergé. Conçue autour du fait qui définit le domaine : **le bureau change
chaque année, et tout ce qu'il possède change avec lui.**

> État : **P1 en cours.** Le cœur du domaine (gouvernance, contenu, passation)
> est implémenté et testé. Ce qui reste est listé plus bas, explicitement.

---

## Démarrer

```bash
make up        # infrastructure : Postgres, PgBouncer, Keycloak, Valkey, MinIO, Redpanda, Meilisearch, Mailpit
make run       # l'API sur http://localhost:8080
make seed      # trois associations réelles réparties sur deux années
make front     # le portail public sur http://localhost:5173
```

`make` sans argument liste les cibles disponibles.

| Service | URL | Identifiants |
|---|---|---|
| API | http://localhost:8080 | jeton Keycloak |
| Keycloak | http://localhost:8081 | `admin` / `$KEYCLOAK_ADMIN_PASSWORD` |
| MinIO | http://localhost:9001 | `$MINIO_USER` / `$MINIO_PASSWORD` |
| Meilisearch | http://localhost:7700 | `$MEILI_KEY` |
| Mailpit | http://localhost:8025 | — |

Utilisateurs de développement (mot de passe `dev`) : `president.bde`,
`tresorier.bde`, `etudiant`, `admin.plateforme`.

---

## L'idée centrale : mandat, pas année

Une **association** est permanente — « BDE » existe pour toujours et porte
l'URL. Un **mandat** est le terme d'un bureau : une période, avec ses pages,
son thème, ses partenaires, son équipe.

Le mandat est clé sur une **période**, pas sur une année :

```sql
periode  tstzrange GENERATED ALWAYS AS (tstzrange(debut_le, fin_le)) STORED,
EXCLUDE USING gist (association_id WITH =, periode WITH &&) WHERE (statut <> 'PREPARATION')
```

Les élections de BDE ont lieu à l'AG, souvent au printemps. Un modèle clé sur
`(association, année)` ne peut pas représenter une passation de juin : il
faudrait soit écraser le bureau en cours — et perdre la trace de qui a
réellement exercé — soit activer le mandat suivant trois mois trop tôt, ce qui
invalide toutes les adhésions en cours. Le modèle par période n'a pas ce
problème, et la non-superposition est garantie par la base.

De la même façon, une adhésion distingue **ce qu'elle couvre** de **qui l'a
vendue** (`couvre_annee_code` / `vendue_par_mandat_id`), sans quoi une campagne
« early bird » de juillet produit des adhésions que le contrôle d'accès refuse
pendant six semaines.

Les **évènements** et les **partenaires** suivent la même règle, et c'est là
qu'elle se voit le mieux. Un partenariat se renégocie chaque année : rattaché à
l'association, « Crédit Mutuel » réapparaîtrait tout seul sur la page de
2026-2027, logo et mention « partenaire officiel » compris, sans que personne
n'ait rien resigné. Rattaché au mandat, il reste sur la page de 2024-2025, où il
est vrai. Le jeu de données de démonstration contient exactement ce cas.

Le corollaire est moins évident : sur une page d'archive, un bloc agenda réglé
sur « à venir » n'a rien à montrer, puisque le mandat est terminé. Appliquer le
filtre à la lettre donnerait une page d'archive à l'agenda vide, laissant croire
que ce bureau n'a rien organisé. Une page de mandat clos affiche donc son agenda
complet — son bilan. La règle est une fonction pure, `SelectionBlocs`, testée à
part : c'est le genre de logique qui se trompe sans jamais lever d'exception.

---

## Ce que la base garantit, et que le code ne pourrait pas

L'essentiel des propriétés du système est imposé par PostgreSQL. C'est
délibéré : du code peut être contourné par un script de maintenance ou un
futur développeur pressé, une contrainte ne l'est pas.

| Garantie | Mécanisme |
|---|---|
| Deux bureaux ne se chevauchent jamais | `EXCLUDE USING gist` |
| Un seul mandat en fonction par association | index unique partiel |
| Un seul président / trésorier par bureau | index unique partiel sur les postes statutaires |
| Au plus une version publiée par page | index unique partiel |
| Le contenu publié est immuable | trigger `bloc_fige` |
| On ne dépublie pas | trigger de transition de statut |
| Un bloc archivé reste revalidable | `PK(type, schema_version)` + FK |
| Une adhésion par personne, asso et année | contrainte d'unicité |
| Une référence de paiement n'active qu'une adhésion | index unique partiel |
| Une clé de média n'est jamais une URL | `CHECK (cle !~ '^https?://' AND cle !~ '\?')` |
| Un média utilisé par une archive est indestructible | FK `media_usage → media_asset` |
| Le total d'une commande = la somme de ses lignes | `CONSTRAINT TRIGGER` différé |
| Les lignes d'une commande payée sont figées | trigger |
| Un droit n'est facturé qu'une fois | `UNIQUE (type_ligne, reference_id)` |
| Un paiement n'est enregistré qu'une fois | `UNIQUE (fournisseur, reference)` |
| Un webhook n'est traité qu'une fois | PK sur l'id d'évènement |
| Le journal comptable est append-only | trigger |

`infra/postgres/verifier-contraintes.sql` (`make verif-contraintes`) vérifie
ces trente-sept garanties contre un vrai PostgreSQL : chaque bloc « doit être
REFUSÉ » doit produire une erreur. Un script qui passe sans erreur signifie
qu'une contrainte a disparu. `ContraintesTemporellesIT` et
`ImmuabiliteContenuIT` font la même chose via Testcontainers.

---

## Modules

```
gouvernance   associations, années, mandats, bureaux, permissions   ← ne dépend de rien
contenu       pages, versions, blocs, thèmes, registre              ← dépend de gouvernance
adhesion      campagnes, tarifs, adhésions                          ← dépend de gouvernance
media         dépôt présigné, métadonnées, résolution d'URL         ← dépend de gouvernance
agenda        évènements d'un mandat, annulations                    ← dépend de gouvernance
partenariat   partenaires d'un mandat, niveaux                       ← dépend de gouvernance
portail       rendu public : compose contenu + bureau + médias        ← orchestration
tresorerie    commandes, paiements, journal comptable               ← dépend de gouvernance + adhesion
passation     orchestration du transfert annuel                     ← dépend de gouvernance + contenu
shared        sécurité, erreurs, configuration (module ouvert)
```

Les frontières sont vérifiées **à la compilation** par Spring Modulith et
ArchUnit : `ApplicationModules.verify()` échoue en CI, pas en revue de code.
C'est ce qui rend crédible la promesse « promouvoir un module en service séparé
est un changement de déploiement, pas une réécriture ».

---

## Sécurité — ce que la v1 a raté

L'audit de la v1 a trouvé 27 routes publiques sur 32, une reprise de compte
sans authentification, des empreintes de mots de passe servies publiquement,
et une clé de signature JWT codée en dur. Les corrections sont structurelles :

- **Refus par défaut.** `anyRequest().authenticated()`, les exceptions sont
  énumérées et se limitent à la lecture publique et aux sondes de santé.
- **`@EnableMethodSecurity` réellement activé.** Dans la v1, `@PreAuthorize`
  était présent mais inerte — pire que son absence, car il se lisait comme une
  protection en revue de code.
- **Aucune implémentation de JWT maison.** Keycloak émet, Spring valide.
- **Les droits par association ne sont jamais dans le jeton.** Ils sont résolus
  par `PolitiqueAcces` à chaque requête, donc révocables immédiatement.
- **Aucune entité JPA n'est sérialisée.** Un test ArchUnit échoue si une
  méthode d'API renvoie un `@Entity`.
- **Les écritures ne visent que le mandat en fonction.** Un mandat clos
  n'accepte plus rien : c'est ce qui rend l'archive fiable.
- **Le dépôt de fichier exige une identité et un droit.** Dans la v1,
  `POST /api/posts/uploadImage` était en `permitAll` et acceptait n'importe
  quel fichier de 20 Mo — hébergement gratuit pour tout internet.
- **La base ne stocke jamais d'URL signée.** Une contrainte `CHECK` refuse
  toute clé ressemblant à une URL, ce qui rend STOR-01 impossible à
  réintroduire, même par un script d'import.
- **Aucun montant ne vient du client.** Le prix est lu dans les tarifs, la
  commande totalisée côté serveur, et le montant encaissé est *comparé* au
  montant dû. La v1 acceptait `{"amount": 1}` depuis le navigateur.
- **Le webhook est vérifié cryptographiquement.** C'est la seule route ouverte
  sans jeton, et elle n'est pas non authentifiée pour autant : la signature
  Stripe fait foi. La v1 n'avait aucun webhook — elle croyait le navigateur.

---

## Tests

```bash
make test      # unitaires + architecture — aucun Docker requis
make verify    # + intégration Testcontainers — Docker requis
```

84 tests unitaires et d'architecture, dont la table de vérité complète des
permissions, le cycle de vie des mandats, l'idempotence de l'activation des
adhésions, le refus d'une URL comme clé d'objet et le rejet des webhooks
illisibles. Les tests d'intégration
(`*IT.java`) exigent un démon Docker et tournent en CI.

---

## Sauvegardes

```bash
make sauvegarde   # dump chiffré + objets, avec rétention
make drill        # exercice de restauration complet, sur une base jetable
```

Le chiffrement est asymétrique : la machine sauvegardée ne détient que la clé
publique, donc elle peut écrire des sauvegardes sans pouvoir relire les
anciennes. L'exercice de restauration ne se contente pas de vérifier que
`pg_restore` est sorti en 0 — il compare le contenu table par table, compare le
schéma, puis **tente sur la base restaurée les écritures qu'elle doit refuser**.
Une restauration qui ramènerait les lignes sans les contraintes produirait une
base qui a l'air correcte et dans laquelle deux bureaux peuvent coexister sur la
même période.

RPO 24 h, RTO 4 h, et ce qui n'est pas couvert (WAL, realm Keycloak, copie hors
site) : [`infra/sauvegarde/README.md`](infra/sauvegarde/README.md).

---

## Reste à faire

Honnêtement, pour que ce fichier ne devienne pas le README de la v1 — qui
documentait MySQL, Jenkins et Docker Compose, dont aucun n'existait :

- [x] ~~**Module `adhesion`**~~ — campagnes, tarifs, adhésions ; prix côté serveur
- [x] ~~**Module `media`**~~ — MinIO, dépôt présigné, clés jamais d'URL
- [ ] **`media-worker`** — variantes WebP/AVIF, EXIF, magic bytes, ClamAV
- [x] ~~**Module `tresorerie`**~~ — Stripe, prix serveur, webhook signé, journal
- [x] ~~**Portail public**~~ — Vite + TS, registre de blocs, thème par mandat, archives
- [x] ~~**Agenda et partenaires**~~ — rattachés au mandat, blocs `EVENT_LIST`,
      `PARTNERS`, `COUNTDOWN` et `EMBED` rendus
- [x] ~~**Tableau de bord**~~ — mandats, pages, éditeur de blocs, publication, OIDC
- [ ] **Profil `delivery`** — configuré, mais pas encore de snapshot ni de cache Valkey
- [ ] **Évènements** — Redpanda tourne, aucun producteur ni consommateur
- [ ] **Observabilité** — actuator et Prometheus exposés ; OTel, Loki, Tempo à venir
- [x] ~~**Sauvegardes**~~ — chiffrées `age`, avec un exercice de restauration
      qui vérifie les données, le schéma *et* les invariants
- [ ] **Déploiement** — k3s, ArgoCD, copie hors site des sauvegardes

L'infrastructure de `docker-compose.dev.yml` est démarrée d'avance pour que
chaque module s'y branche sans changer la boucle de développement ; tout ce qui
y figure n'est pas encore utilisé par le code.
