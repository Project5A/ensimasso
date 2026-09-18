# ENSIMAsso v2 — plateforme des associations étudiantes

Réécriture de la plateforme des associations de l'ENSIM, en monolithe modulaire
auto-hébergé. Conçue autour du fait qui définit le domaine : **le bureau change
chaque année, et tout ce qu'il possède change avec lui.**

> État : **P1 en cours.** Le cœur du domaine (gouvernance, contenu, passation)
> est implémenté et testé. Ce qui reste est listé plus bas, explicitement.

---

## Démarrer

```bash
make up        # infrastructure réellement utilisée : Postgres, PgBouncer, Keycloak, Valkey, MinIO
make run       # l'API sur http://localhost:8080
make seed      # trois associations réelles réparties sur deux années
make front     # le portail public sur http://localhost:5173
```

`make` sans argument liste les cibles disponibles.

Le parcours sans Docker — `make init`, `make test`, `make front-test` — est
rejoué en intégration continue **depuis un clone neuf, sans cache**. Ce n'est
pas de la ceinture-bretelles : les autres jobs appellent les outils directement
et sont passés au vert pendant que le Makefile était cassé, une enveloppe Maven
absente rendant toutes les cibles inutilisables. Le parcours qui compte est
celui que suivent les gens, donc c'est celui qu'il faut exécuter.

| Service | URL | Identifiants |
|---|---|---|
| API | http://localhost:8080 | jeton Keycloak |
| Keycloak | http://localhost:8081 | `admin` / `$KEYCLOAK_ADMIN_PASSWORD` |
| MinIO | http://localhost:9001 | `$MINIO_USER` / `$MINIO_PASSWORD` |
| Valkey | localhost:6379 | — |

`make up` ne démarre **que** ce dont le code se sert. Redpanda, Meilisearch et
Mailpit sont déclarés dans le fichier compose mais rangés dans un profil
`futur` : aucune ligne de code ne les référence aujourd'hui, et trois
conteneurs qui tournent pour rien coûtent de la mémoire à chaque contributeur
tout en laissant croire que le système publie des évènements, indexe une
recherche et envoie des courriels. `make up-tout` les démarre pour qui
travaille dessus — et le jour où l'un d'eux est branché, un test exige qu'il
sorte du profil.

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
| Le contenu publié ne se modifie pas | trigger `bloc_fige` |
| Une archive ne se supprime pas non plus | gardes de suppression sur `mandat`, `page`, `page_version` |
| Un thème publié est figé, et ne se dépublie pas | triggers `theme_fige` et `theme_transition` |
| On ne dépublie pas | trigger de transition de statut |
| Un bloc archivé reste revalidable | `PK(type, schema_version)` + FK |
| Une seule adhésion VIVANTE par personne, asso et année | index unique partiel sur `ACTIVE` et `EN_ATTENTE_PAIEMENT` |
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
ces garanties une à une contre un vrai PostgreSQL — quarante-quatre cas : chaque bloc « doit être
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

181 tests unitaires et d'architecture, dont la table de vérité complète des
permissions, le cycle de vie des mandats, l'idempotence de l'activation des
adhésions, le refus d'une URL comme clé d'objet, le rejet des webhooks
illisibles et la reconnaissance d'un SVG déposé sous `image/png`. Les tests
d'intégration (`*IT.java`) exigent un démon Docker et tournent en CI.

`ContratApiTest` confronte le client d'API du front aux routes réellement
déclarées par les contrôleurs. C'est un défaut constaté dans la v1, pas une
précaution théorique : le tableau de bord appelait `PUT /api/events/{id}`,
`DELETE /api/events/{id}` et `DELETE /api/posts/{id}`, dont aucun n'était
implémenté — le bouton existait, le clic produisait un 405, et rien dans la
chaîne de construction ne le disait. Le test a été vérifié à l'envers : en
réintroduisant l'un de ces trois appels, il échoue en le nommant.

---

## Les constats de l'audit, vérifiés plutôt que cochés

`NonRegressionAuditTest` reprend les constats de l'audit de la v1 et affirme,
pour chacun, une propriété que la v1 n'avait pas — donc un test qui aurait
échoué sur elle. Entre autres : le nombre d'exceptions `permitAll` est plafonné
à trois, aucun secret ne réapparaît dans le dépôt, aucun `FetchType.EAGER`, et
le README ne promet aucune commande `make` ni aucun fichier qui n'existe pas.

Ce dernier point mérite d'être dit : le constat OPS-04 reprochait à la v1 un
README décrivant MySQL, Jenkins et `docker-compose up`, dont aucun n'existait.
Un document qui décrit autre chose que le système fait perdre plus de temps
qu'il n'en fait gagner, et ce fichier-ci n'est pas exempt du risque. Il est donc
vérifié à chaque build.

Ces tests existent parce qu'une case cochée ne tient rien. Relire l'audit après
coup a révélé que **SEC-07 — aucune limitation de débit — était resté ouvert
pendant toute la réécriture**, alors même que les autres constats de sécurité
étaient traités. Rien ne le signalait ; il fallait relire.

---

## Ce que le système raconte de lui-même

Quatre mesures, choisies parce que chacune répond à une question qu'on se pose
vraiment. Une métrique qu'on ne sait pas relier à une décision est une métrique
qu'on paiera à stocker sans jamais la regarder.

| Métrique | La question |
|---|---|
| `ensimasso_portail_cache_total{resultat}` | le cache sert-il à quelque chose ? |
| `ensimasso_portail_rendu_seconds` (p50, p95) | combien coûte une page quand il n'a pas servi ? |
| `ensimasso_media_depot_total{resultat,motif}` | est-ce qu'on nous sonde ? |
| `ensimasso_tresorerie_webhook_total{resultat}` | les paiements arrivent-ils, et sinon pourquoi ? |
| `ensimasso_gouvernance_mandats_en_fonction` | combien d'associations sont réellement dirigées ? |

La dernière est une **jauge**, et c'est sa chute qui compte : zéro bureau en
fonction signifie qu'aucune page publique ne s'affiche plus. Aucune métrique
technique ne dit cela — le processus va très bien, la base répond, et le site
est vide.

Chaque ligne de journal porte l'identifiant de trace de la requête qui l'a
produite, ce qui relie « ça a planté à 19 h » aux lignes qui le racontent.
`LOG_FORMAT=ecs` bascule les journaux en un objet JSON par ligne, que Loki
indexe sans expression régulière.

**Ce qui n'est pas inclus, et pourquoi.** L'exporteur OTLP tire okhttp et la
bibliothèque standard Kotlin — plusieurs mégaoctets — pour envoyer des traces
vers un Tempo qui n'existe pas encore. C'est exactement le travers reproché à
la v1, qui servait three.js et un modèle 3-D de 2,9 Mo à des visiteurs qui n'en
voyaient jamais rien. Une dépendance suffira le jour où il y aura un collecteur
en face.

---

## Le cache, et pourquoi il n'a pas d'invalidation

La clé d'une page en cache contient l'identifiant de sa **version publiée** :

```
portail:v1:bde:{mandat}:accueil:{versionId}:courant
```

Publier crée une nouvelle version, donc écrit une nouvelle clé ; l'ancienne
entrée s'éteint toute seule. L'invalidation — la partie où l'on se trompe —
n'existe pas comme problème.

Deux bornes, toutes deux dans le code plutôt que dans un commentaire de
configuration :

- **La durée est plafonnée par la validité des URL de médias.** Une page gardée
  plus longtemps que ses URL signées afficherait des images mortes : ce serait
  STOR-01 réintroduit par la porte de derrière. Une valeur trop généreuse dans
  un `.env` ne peut pas contourner la règle.
- **Le cache en mémoire est borné en nombre d'entrées.** Un cache sans borne est
  une fuite de mémoire qu'on ne découvre qu'en production.

Ce que la clé ne couvre pas, et qui accuse donc un retard borné par la durée :
le thème du mandat, le menu, l'agenda et les partenaires. Ces données changent
sans créer de version de page. C'est le prix d'une clé calculable sans faire le
travail qu'on cherche à éviter, et c'est pourquoi la durée est courte.

Vérifié sur l'application en fonctionnement : une modification faite
directement en base n'apparaît pas tant que l'entrée vit, puis apparaît d'elle-même
à son expiration. `CacheValkeyIT` s'exécute contre un vrai serveur — et échoue
si aucun n'est joignable, au lieu de se désactiver en silence.

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

### Ce qui existe mais que personne ne peut atteindre

D'abord ceci, parce que c'est le plus gros écart entre ce dépôt et un
produit, et que la liste de cases ci-dessous le rendait invisible : elle met
sur la même ligne « Module `adhesion` », qui désigne du code serveur, et
« Tableau de bord », qui désigne un écran.

Deux modules sont écrits, autorisés, testés — et **aucun écran ne les
appelle**. Quatorze routes qu'aucun humain ne peut atteindre autrement
qu'avec `curl` :

| Module | Routes | Ce qui manque |
|---|---|---|
| `/api/adhesions` | 8 | l'écran de campagne côté bureau, et l'adhésion côté étudiant |
| `/api/tresorerie` | 6 | la page de paiement, le suivi de commande, le journal |

Ils étaient quatre. `/api/medias` est sorti de la liste — la médiathèque a son
écran, et avec elle `evenement.mediaKey` et `partenaire.logoMediaKey`, tous
deux **rendus par le portail public**, se choisissent enfin autrement que par
un INSERT à la main. `/api/passations` aussi : les quatre étapes se déroulent
depuis le tableau de bord.

Il manque cependant un **annuaire des personnes**, et cela se voit à l'écran de
passation : le système ne stocke aucun nom — `membre_bureau.personne_id` est le
`sub` Keycloak et rien d'autre. On désigne donc quelqu'un par l'identifiant de
son compte, que chacun lit sur cet écran et transmet. C'est utilisable, ce
n'est pas satisfaisant, et c'est aussi pourquoi le trombinoscope public affiche
des postes et des photos, jamais des noms.

`ContratApiTest.modulesSansEcran` fige cette liste et vérifie qu'elle est
exacte dans les deux sens : le jour où l'un de ces écrans est écrit, le test
devient rouge et oblige à corriger ce paragraphe.

### Le reste

- [x] ~~**Module `adhesion`**~~ — campagnes, tarifs, adhésions ; prix côté serveur
- [x] ~~**Module `media`**~~ — MinIO, dépôt présigné, clés jamais d'URL
- [x] ~~**Vérification de signature au dépôt**~~ — les octets réels doivent
      correspondre au type annoncé ; un SVG ou un document HTML déposé sous
      `image/png` est refusé et effacé
- [ ] **`media-worker`** — variantes WebP/AVIF, retrait de l'EXIF, ClamAV :
      tout ce qui suppose de *décoder* le fichier, donc un processus isolé
- [x] ~~**Module `tresorerie`**~~ — Stripe, prix serveur, webhook signé, journal
- [x] ~~**Portail public**~~ — Vite + TS, registre de blocs, thème par mandat, archives
- [x] ~~**Agenda et partenaires**~~ — rattachés au mandat, blocs `EVENT_LIST`,
      `PARTNERS`, `COUNTDOWN` et `EMBED` rendus, gérés depuis le tableau de bord
- [x] ~~**Tableau de bord**~~ — mandats, pages, éditeur de blocs, publication, OIDC
- [x] ~~**Aperçu d'un brouillon**~~ — le même composant de rendu que le site
      public, pour qu'un aperçu ne puisse pas diverger de ce qui sera publié
- [x] ~~**Cache du chemin public**~~ — clé versionnée, donc aucune invalidation
      à écrire ; en mémoire par défaut, Valkey sur le profil `delivery`
- [ ] **Profil `delivery`** — cache branché ; le snapshot lecture seule reste à faire
- [ ] **Évènements** — Redpanda est déclaré, rangé dans le profil `futur` tant
      qu'aucun producteur ni consommateur n'existe
- [x] ~~**Observabilité**~~ — métriques métier, identifiant de trace dans chaque
      ligne de journal, journaux JSON en production
- [ ] **Export des traces** — le pont OpenTelemetry est là ; l'exporteur OTLP
      attend qu'il y ait un Tempo en face
- [x] ~~**Sauvegardes**~~ — chiffrées `age`, avec un exercice de restauration
      qui vérifie les données, le schéma *et* les invariants
- [x] ~~**Manifestes de déploiement**~~ — k3s et ArgoCD, validés contre les
      schémas et leurs renvois — mais jamais appliqués à un cluster
- [ ] **Premier déploiement réel** — et la copie hors site des sauvegardes

`docker-compose.dev.yml` déclare aussi ce qui n'est pas encore branché, pour
que le jour venu un module s'y raccorde sans changer la boucle de
développement — mais ces services-là ne démarrent pas par défaut, et
`InfrastructureUtiliseeTest` vérifie dans les deux sens que ce qui démarre est
utilisé et que ce qui est utilisé démarre.
