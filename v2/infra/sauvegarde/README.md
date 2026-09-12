# Sauvegarde et restauration

> Une sauvegarde jamais restaurée n'est pas une sauvegarde, c'est une intention.
> `drill-restauration.sh` existe pour que cette phrase ne reste pas décorative.

## Ce que ces scripts font

| Script | Rôle |
|---|---|
| `sauvegarder.sh` | Dump chiffré de la base + archive chiffrée du stockage objet, avec rétention |
| `restaurer.sh` | Vérification d'intégrité, déchiffrement, restauration dans une base |
| `drill-restauration.sh` | Fait la boucle complète et **échoue** si un maillon casse |
| `commun.sh` | Configuration et empreintes (contenu et schéma) |

## Modèle de menace

Le chiffrement est **asymétrique** (`age`), et ce choix a une conséquence
concrète : la machine sauvegardée ne détient que la clé **publique**. Elle peut
écrire des sauvegardes, elle ne peut pas lire les anciennes. Un attaquant qui
prend le contrôle du serveur d'application n'obtient donc pas l'historique des
adhérents — ce qu'une phrase de passe partagée lui aurait donné.

| Clé | Où elle vit | Où elle ne doit jamais être |
|---|---|---|
| `AGE_DESTINATAIRE` (publique) | Serveur d'application, CI | — (elle est publique) |
| `AGE_IDENTITE` (privée) | Gestionnaire de secrets, copie papier en coffre | Serveur d'application, dépôt git, image de conteneur |

Générer la paire :

```bash
age-keygen -o identite.txt          # clé privée — à mettre à l'abri, puis à effacer d'ici
age-keygen -y identite.txt          # clé publique — à publier dans la config du serveur
```

**Si la clé privée est perdue, les sauvegardes sont définitivement illisibles.**
C'est le prix du modèle ci-dessus, et il doit être assumé explicitement : deux
détenteurs, deux endroits, et une vérification annuelle que la clé déchiffre
encore quelque chose (le drill le fait avec une clé éphémère, pas avec la vraie
— il faut donc le faire à part, une fois par an).

## Usage

```bash
# Sauvegarde (sur la machine de production, clé publique seule)
AGE_DESTINATAIRE=age1… CHEMIN_OBJETS=/var/lib/minio ./sauvegarder.sh

# Restauration (sur la machine de secours, clé privée présente)
AGE_IDENTITE=/secrets/identite.txt ./restaurer.sh 2026-09-12T03-00-00Z ensimasso

# Exercice complet, sur une base jetable
PGHOST=… PGUSER=… ./drill-restauration.sh ensimasso
```

Toute la configuration passe par l'environnement : `PGHOST`, `PGPORT`, `PGUSER`,
`PGDATABASE`, `DEPOT`, `CHEMIN_OBJETS`, `DESTINATION_DISTANTE`,
`RETENTION_QUOTIDIENNE` / `_HEBDO` / `_MENSUELLE`.

## Ce que l'exercice vérifie réellement

Quatorze vérifications, dont les six dernières sont les seules qui comptent vraiment :

1. le dump porte bien l'en-tête `age` — il est chiffré, pas seulement compressé ;
2. une **autre** clé ne le déchiffre pas ;
3. le manifeste (tailles, SHA-256) est lisible **sans** la clé privée, pour
   qu'on puisse surveiller l'intégrité sans détenir le secret ;
4. une archive dont **un seul octet** a été modifié est refusée ;
5. et aucune base n'est créée à partir d'elle ;
6. écraser une base existante exige `FORCER=oui` ;
7. les données restaurées sont identiques, table par table (`count` + `md5` du
   contenu trié) ;
8. le schéma restauré porte les mêmes contraintes, index, déclencheurs et
   fonctions ;
9. *(témoin)* un mandat en `PREPARATION` **peut** chevaucher — le prédicat
   partiel de la contrainte a survécu lui aussi ;
10. un mandat qui en chevauche un autre est **refusé** (`EXCLUDE … USING gist`) ;
11. *(témoin)* une commande dont le total égale ses lignes **passe** ;
12. une commande dont le total ne correspond pas à ses lignes est refusée **au
    COMMIT** (contrainte différée) ;
13. les blocs d'une version de page publiée ne peuvent pas être supprimés ;
14. une version publiée ne peut pas être dépubliée.

Les deux **témoins positifs** (9 et 11) ne sont pas du remplissage : sans eux,
« refusé » ne distinguerait pas un invariant qui tient d'un script qui ne
s'exécute pas du tout. Et chaque refus est vérifié **sur le message d'erreur** :
un rejet pour une autre raison — colonne absente, faute de frappe — compte comme
un échec, pas comme une réussite.

### Pourquoi vérifier le schéma et pas seulement les lignes

Toute la conception de la v2 repose sur des invariants tenus par la base
elle-même. Une restauration qui ramènerait les données sans les contraintes
produirait une base qui *a l'air* correcte, qui répond correctement aux
lectures, et dans laquelle deux bureaux peuvent désormais coexister sur la même
période. Personne ne s'en apercevrait avant la première passation.

L'exercice a été validé à l'envers : sur une copie de la base dont la contrainte
`mandat_pas_de_chevauchement` avait été retirée, il sort en **échec** au point 10
alors que les points 7 et 8 passent — parce que les données sont bien identiques
et que le schéma source et le schéma restauré sont, eux aussi, identiquement
amputés. Une vérification qui ne peut pas échouer ne vérifie rien.

## RPO et RTO

| | Objectif | Fondement |
|---|---|---|
| **RPO** (perte de données acceptable) | **24 h** | Une sauvegarde quotidienne. Aucun archivage WAL : la restauration se fait au dump, pas à l'instant. |
| **RTO** (délai de remise en service) | **4 h** | Restauration technique de l'ordre de la minute ; le reste est humain — constater la panne, retrouver la clé, provisionner un hôte. |

Mesures de l'exercice sur le jeu de données de démonstration (50 lignes,
21 tables, PostgreSQL 16.13) : archive chiffrée de **68 Kio**, sauvegarde et
restauration **< 1 s** chacune. Ces chiffres ne disent rien du volume réel : ils
établissent que la chaîne fonctionne, pas qu'elle tiendra à l'échelle. À
remesurer sur un dump de production, et à réviser si la restauration dépasse
quelques minutes.

Pour descendre le RPO sous les 24 h il faudrait archiver les WAL
(`archive_command` + restauration à un point dans le temps). Ce n'est pas fait,
et c'est une décision, pas un oubli : pour une plateforme associative dont
l'écriture est concentrée sur quelques semaines de rentrée, perdre au pire une
journée d'adhésions est réparable — la campagne est rejouable, les paiements
sont réconciliables avec Stripe. Si la billetterie monte en charge, ce calcul
change et le WAL devient nécessaire.

## Planification

```
# /etc/cron.d/ensimasso-sauvegarde
0 3 * * *  ensimasso  AGE_DESTINATAIRE=age1… /opt/ensimasso/sauvegarde/sauvegarder.sh
0 4 * * 0  ensimasso  AGE_IDENTITE=/secrets/identite.txt /opt/ensimasso/sauvegarde/drill-restauration.sh
```

L'exercice tourne **chaque semaine**, pas une fois à l'installation : il sort en
code 1 quand quelque chose casse, ce qui le rend surveillable comme n'importe
quel autre échec.

## Ce qui n'est pas couvert

À écrire noir sur blanc, parce que ce sont les trous par lesquels une
restauration échoue le jour où elle compte :

- **Pas de restauration à un instant T.** Pas d'archivage WAL ; voir RPO.
- **Le realm Keycloak n'est pas sauvegardé ici.** Il vit dans sa propre base.
  Sans lui, les données sont là mais plus personne ne peut se connecter.
- **Copie hors site non gérée.** `DESTINATION_DISTANTE` n'est qu'un
  avertissement : le script crie si elle est absente, il ne synchronise rien.
  Une sauvegarde qui reste sur la machine sauvegardée ne protège ni d'une perte
  de disque, ni d'un chiffrement par rançongiciel.
- **La restauration exige de pouvoir créer des extensions** (`btree_gist`,
  `pgcrypto`). Le rôle qui restaure doit en avoir le droit, ou l'extension doit
  préexister sur la cible.
- **Le stockage objet n'est sauvegardé que si `CHEMIN_OBJETS` est défini.** La
  base ne contient que des clés d'objets : sans les octets, les pages d'archive
  s'affichent sans leurs images.
