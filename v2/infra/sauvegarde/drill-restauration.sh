#!/usr/bin/env bash
#
# Exercice de restauration — à exécuter périodiquement, en CI ou à la main.
#
#   PGHOST=… PGPORT=… PGUSER=… ./drill-restauration.sh [base_source]
#
# Une sauvegarde n'est pas une sauvegarde tant qu'elle n'a pas été restaurée.
# Ce script fait la boucle complète sur une base réelle et échoue bruyamment
# si l'un des maillons casse :
#
#   1. la sauvegarde est chiffrée, et illisible sans la bonne clé ;
#   2. une archive corrompue est DÉTECTÉE, pas restaurée silencieusement ;
#   3. écraser une base existante exige un geste explicite ;
#   4. les données restaurées sont identiques, table par table ;
#   5. le schéma restauré porte encore toutes ses contraintes ;
#   6. les invariants métier sont réellement appliqués après restauration.
#
# Le point 6 est le seul qui prouve quelque chose d'utile : on ne vérifie pas
# que pg_restore est sorti en 0, on vérifie que la base restaurée refuse encore
# ce qu'elle doit refuser — et pour la bonne raison, en lisant le message
# d'erreur. Un refus obtenu par accident (faute de frappe, colonne absente)
# serait indiscernable d'un invariant qui tient, et tout aussi rassurant.
set -euo pipefail
IFS=$'\n\t'
cd "$(dirname "$0")"

BASE_SOURCE="${1:-${PGDATABASE:-ensimasso}}"

# Environnement jetable : dépôt temporaire, paire de clés éphémère, base neuve.
TRAVAIL="$(mktemp -d -t drill-ensimasso-XXXXXX)"
export DEPOT="$TRAVAIL/depot"
export PGDATABASE="$BASE_SOURCE"
BASE_CIBLE="drill_$(date -u +%Y%m%d_%H%M%S)"
ECHECS=0

# shellcheck source=commun.sh
source ./commun.sh

nettoyer() {
  for b in "$BASE_CIBLE" "${BASE_CIBLE}_corrompue"; do
    psql -X -q -d postgres -c "drop database if exists \"$b\" with (force)" >/dev/null 2>&1 || true
  done
  rm -rf "$TRAVAIL"
}
trap nettoyer EXIT

verifier() {  # verifier <description> <statut>
  if [ "$2" -eq 0 ]; then
    printf '  \033[32m✓\033[0m %s\n' "$1"
  else
    printf '  \033[31m✗ %s\033[0m\n' "$1" >&2
    ECHECS=$((ECHECS + 1))
  fi
}

# Exécute un lot SQL sur la base restaurée et vérifie qu'il est refusé POUR LA
# RAISON attendue : le message d'erreur doit contenir <motif>.
attendu_refus() {  # attendu_refus <description> <motif> <sql>
  local desc="$1" motif="$2" sql="$3" sortie statut=0
  sortie="$(psql -X -q -v ON_ERROR_STOP=1 -d "$BASE_CIBLE" 2>&1 <<<"$sql")" || statut=$?
  if [ "$statut" -eq 0 ]; then
    verifier "$desc — ACCEPTÉ alors qu'il devait être refusé" 1
  elif printf '%s' "$sortie" | grep -qF "$motif"; then
    verifier "$desc" 0
  else
    verifier "$desc — refusé, mais pas par « $motif »" 1
    printf '%s\n' "$sortie" >&2
  fi
}

# Témoin positif : la même forme d'écriture, mais licite, doit passer. Sans
# lui, un test « refusé » ne distingue pas un invariant qui tient d'un script
# qui ne s'exécute pas du tout.
attendu_accepte() {  # attendu_accepte <description> <sql>
  local desc="$1" sql="$2" sortie statut=0
  sortie="$(psql -X -q -v ON_ERROR_STOP=1 -d "$BASE_CIBLE" 2>&1 <<<"$sql")" || statut=$?
  verifier "$desc" "$statut"
  [ "$statut" -eq 0 ] || printf '%s\n' "$sortie" >&2
}

exiger_outil age-keygen
exiger_outil pg_dump
exiger_outil pg_restore
exiger_outil numfmt

titre "Exercice de restauration — source : $BASE_SOURCE"

# --- 0. clés éphémères ----------------------------------------------------
age-keygen -o "$TRAVAIL/identite.txt" 2>/dev/null
export AGE_IDENTITE="$TRAVAIL/identite.txt"
AGE_DESTINATAIRE="$(age-keygen -y "$AGE_IDENTITE")"
export AGE_DESTINATAIRE
age-keygen -o "$TRAVAIL/autre.txt" 2>/dev/null   # clé d'un tiers, pour le test 1b

# --- 1. sauvegarde --------------------------------------------------------
titre "1. Sauvegarde"
DEBUT=$(date +%s)
./sauvegarder.sh >"$TRAVAIL/sauvegarde.log" 2>&1 || {
  cat "$TRAVAIL/sauvegarde.log" >&2
  echec "la sauvegarde a échoué"
}
DUREE_SAUVEGARDE=$(( $(date +%s) - DEBUT ))
HORODATAGE="$(find "$DEPOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r | head -1)"
ARCHIVE="$DEPOT/$HORODATAGE"
TAILLE_DUMP=$(stat -c %s "$ARCHIVE/base.dump.age")
info "archive : $HORODATAGE ($(numfmt --to=iec "$TAILLE_DUMP")) en ${DUREE_SAUVEGARDE}s"

statut=0; head -c 21 "$ARCHIVE/base.dump.age" | grep -q 'age-encryption.org/v1' || statut=$?
verifier "le dump est chiffré (en-tête age présent)" "$statut"

! age -d -i "$TRAVAIL/autre.txt" "$ARCHIVE/base.dump.age" >/dev/null 2>&1
verifier "une clé tierce ne déchiffre pas l'archive" $?

statut=0; grep -q '^version_pg=' "$ARCHIVE/manifeste.txt" || statut=$?
verifier "le manifeste est vérifiable sans la clé privée" "$statut"

# --- 1 bis. la rétention, c'est-à-dire le seul chemin qui EFFACE ----------
titre "1 bis. Rétention"
# L'exercice ne lançait la sauvegarde qu'UNE fois, sur un dépôt vide. Or la
# rétention ne fait rien tant qu'il n'y a qu'une archive : le seul chemin du
# système qui SUPPRIME des sauvegardes n'était donc jamais parcouru. C'est ainsi
# qu'un défaut de jointure de chaîne — la rétention purgeait TOUT, y compris
# l'archive qu'elle venait d'écrire — a pu vivre sans être vu : un drill qui
# n'exerce pas l'effacement ne démontre pas que les sauvegardes survivent.
#
# On force donc de l'ancien dans le dépôt, puis on sauvegarde une SECONDE fois.
for jours in 1 9 20 100; do
  ancienne="$DEPOT/$(date -u -d "-$jours day" +%Y-%m-%dT%H-%M-%SZ)"
  mkdir -p "$ancienne"
  cp "$ARCHIVE/manifeste.txt" "$ancienne/manifeste.txt"
done
AVANT_RETENTION=$(find "$DEPOT" -mindepth 1 -maxdepth 1 -type d | wc -l)

# Politique délibérément serrée pour CETTE exécution : avec les valeurs par
# défaut (7 quotidiennes), cinq archives tiennent toutes et la rétention ne
# supprimerait rien — l'exercice repasserait à côté du chemin destructeur, ce
# qui est exactement le défaut qu'on corrige. À 1/1/1, au plus trois survivent.
RETENTION_QUOTIDIENNE=1 RETENTION_HEBDO=1 RETENTION_MENSUELLE=1 \
  ./sauvegarder.sh >"$TRAVAIL/sauvegarde2.log" 2>&1 || {
    cat "$TRAVAIL/sauvegarde2.log" >&2
    echec "la seconde sauvegarde a échoué"
  }
APRES_RETENTION=$(find "$DEPOT" -mindepth 1 -maxdepth 1 -type d | wc -l)
info "dépôt : $AVANT_RETENTION archives avant, $APRES_RETENTION après la rétention"

statut=0; [ "$APRES_RETENTION" -gt 0 ] || statut=1
verifier "la rétention laisse au moins une sauvegarde" "$statut"

# Et LA plus récente, celle qu'on vient d'écrire, doit être là : c'est
# exactement celle que la rétention effaçait.
DERNIERE="$(find "$DEPOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r | head -1)"
statut=0; [ -s "$DEPOT/$DERNIERE/base.dump.age" ] || statut=1
verifier "la sauvegarde qui vient d'être écrite survit à sa propre rétention" "$statut"

# Le témoin inverse : une rétention qui ne supprime jamais rien n'est pas une
# rétention. Avec 5 archives dont une vieille de 40 jours, elle doit mordre.
statut=0; [ "$APRES_RETENTION" -lt "$AVANT_RETENTION" ] || statut=1
verifier "la rétention supprime réellement les archives hors politique" "$statut"

ARCHIVE="$DEPOT/$DERNIERE"

# --- 2. une archive corrompue doit être refusée ---------------------------
titre "2. Détection de corruption"
cp -r "$ARCHIVE" "$DEPOT/corrompue"
printf '\x00' | dd of="$DEPOT/corrompue/base.dump.age" bs=1 seek=200 count=1 \
  conv=notrunc status=none

! FORCER=oui ./restaurer.sh corrompue "${BASE_CIBLE}_corrompue" >/dev/null 2>&1
verifier "une archive altérée d'un seul octet est refusée" $?

statut=0
psql -X -q -t -A -d postgres -c \
  "select 1 from pg_database where datname='${BASE_CIBLE}_corrompue'" | grep -q 1 || statut=$?
verifier "aucune base n'est créée à partir d'une archive altérée" $(( statut == 0 ? 1 : 0 ))

# --- 3. restauration ------------------------------------------------------
titre "3. Restauration vers $BASE_CIBLE"
EMPREINTE_SOURCE="$(empreinte_base "$BASE_SOURCE")"
SCHEMA_SOURCE="$(empreinte_schema "$BASE_SOURCE")"

DEBUT=$(date +%s)
./restaurer.sh "$HORODATAGE" "$BASE_CIBLE" >"$TRAVAIL/restauration.log" 2>&1 || {
  cat "$TRAVAIL/restauration.log" >&2
  echec "la restauration a échoué"
}
DUREE_RESTAURATION=$(( $(date +%s) - DEBUT ))
info "restauration en ${DUREE_RESTAURATION}s"

! ./restaurer.sh "$HORODATAGE" "$BASE_CIBLE" >/dev/null 2>&1
verifier "refuse d'écraser une base existante sans FORCER=oui" $?

# --- 4. les données sont-elles identiques ? -------------------------------
titre "4. Comparaison des données"
EMPREINTE_CIBLE="$(empreinte_base "$BASE_CIBLE")"
if [ "$EMPREINTE_SOURCE" = "$EMPREINTE_CIBLE" ]; then
  verifier "empreinte identique sur $(printf '%s\n' "$EMPREINTE_SOURCE" | wc -l) tables" 0
else
  verifier "empreinte des données identique" 1
  diff <(printf '%s\n' "$EMPREINTE_SOURCE") <(printf '%s\n' "$EMPREINTE_CIBLE") >&2 || true
fi

# --- 5. le schéma a-t-il survécu ? ----------------------------------------
titre "5. Comparaison du schéma"
SCHEMA_CIBLE="$(empreinte_schema "$BASE_CIBLE")"
if [ "$SCHEMA_SOURCE" = "$SCHEMA_CIBLE" ]; then
  verifier "contraintes, index, déclencheurs et fonctions identiques ($(printf '%s\n' "$SCHEMA_SOURCE" | wc -l) objets)" 0
else
  verifier "schéma identique" 1
  diff <(printf '%s\n' "$SCHEMA_SOURCE") <(printf '%s\n' "$SCHEMA_CIBLE") >&2 || true
fi

# --- 6. les invariants sont-ils encore appliqués ? ------------------------
titre "6. Invariants métier sur la base restaurée"

psql -X -q -v ON_ERROR_STOP=1 -d "$BASE_CIBLE" >/dev/null <<'SQL'
insert into annee_universitaire (code, debut, fin) values
  ('9998-9999', '9998-09-01', '9999-08-31'),
  ('9997-9998', '9997-09-01', '9998-08-31')
on conflict (code) do nothing;
SQL

# 6a — chevauchement de mandats. Le témoin utilise le statut PREPARATION, que
# le prédicat partiel de la contrainte exclut : il vérifie donc aussi que ce
# prédicat a survécu, et pas seulement la contrainte.
attendu_accepte "témoin : un mandat PREPARATION peut chevaucher (prédicat partiel)" "
begin;
insert into mandat (association_id, annee_code, statut, debut_le, fin_le)
select m.association_id, '9998-9999', 'PREPARATION',
       m.debut_le + interval '1 day', m.debut_le + interval '30 days'
  from mandat m where m.statut <> 'PREPARATION' order by m.debut_le limit 1;
commit;"

attendu_refus "chevauchement de mandats refusé (EXCLUDE gist)" "mandat_pas_de_chevauchement" "
begin;
insert into mandat (association_id, annee_code, statut, debut_le, fin_le)
select m.association_id, '9997-9998', 'CLOS',
       m.debut_le + interval '1 day', m.debut_le + interval '30 days'
  from mandat m where m.statut <> 'PREPARATION' order by m.debut_le limit 1;
commit;"

# 6b — invariant comptable, vérifié au COMMIT (contrainte différée).
attendu_accepte "témoin : commande dont le total égale ses lignes" "
begin;
insert into commande (id, personne_id, association_id, statut, montant_total_cents)
values ('aaaaaaaa-0000-4000-8000-000000000001', gen_random_uuid(),
        (select id from association order by slug limit 1), 'OUVERTE', 1000);
insert into ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents)
values ('aaaaaaaa-0000-4000-8000-000000000001', 'ADHESION', gen_random_uuid(), 'drill', 1000);
commit;"

attendu_refus "total de commande incohérent refusé (trigger différé)" "total incoherent" "
begin;
insert into commande (id, personne_id, association_id, statut, montant_total_cents)
values ('aaaaaaaa-0000-4000-8000-000000000002', gen_random_uuid(),
        (select id from association order by slug limit 1), 'OUVERTE', 1000);
insert into ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents)
values ('aaaaaaaa-0000-4000-8000-000000000002', 'ADHESION', gen_random_uuid(), 'drill', 500);
commit;"

# 6c — une version publiée est de l'histoire : elle ne se réécrit pas.
attendu_refus "suppression d'un bloc publié refusée" "bloc fig" "
begin;
delete from bloc
 where page_version_id in (select id from page_version where statut = 'PUBLIEE');
commit;"

attendu_refus "dépublication d'une version refusée" "transition de version interdite" "
begin;
update page_version set statut = 'BROUILLON' where statut = 'PUBLIEE';
commit;"

# --- rapport --------------------------------------------------------------
LIGNES_SOURCE="$(psql -X -q -t -A -d "$BASE_SOURCE" -c \
  "select coalesce(sum(n_live_tup),0) from pg_stat_user_tables")"

titre "Rapport"
printf '  base                 : %s (%s lignes)\n' "$BASE_SOURCE" "$LIGNES_SOURCE"
printf '  taille chiffrée      : %s\n' "$(numfmt --to=iec "$TAILLE_DUMP")"
printf '  durée sauvegarde     : %ss\n' "$DUREE_SAUVEGARDE"
printf '  durée restauration   : %ss\n' "$DUREE_RESTAURATION"
printf '  vérifications        : %s échec(s)\n' "$ECHECS"

if [ "$ECHECS" -eq 0 ]; then
  vert "Exercice réussi : la sauvegarde du $HORODATAGE est restaurable et complète."
else
  echec "$ECHECS vérification(s) en échec — la sauvegarde n'est PAS fiable en l'état."
fi
