#!/usr/bin/env bash
#
# Restauration d'une sauvegarde dans une base.
#
#   ./restaurer.sh <horodatage> [base_cible]
#
# La clé privée (AGE_IDENTITE) n'est nécessaire QUE pour cette opération, et
# ne doit exister que sur la machine qui restaure.
set -euo pipefail
IFS=$'\n\t'
cd "$(dirname "$0")"
# shellcheck source=commun.sh
source ./commun.sh

exiger_outil pg_restore
exiger_outil age

HORODATAGE="${1:-}"
BASE_CIBLE="${2:-$PGDATABASE}"
[ -n "$HORODATAGE" ] || echec "usage : $0 <horodatage> [base_cible]"
[ -n "$AGE_IDENTITE" ] || echec "AGE_IDENTITE (clé privée age) est requis pour restaurer"

SOURCE="$DEPOT/$HORODATAGE"
[ -d "$SOURCE" ] || echec "sauvegarde introuvable : $SOURCE"

titre "Restauration de $HORODATAGE vers $BASE_CIBLE"

# Vérifier l'intégrité AVANT de déchiffrer : une archive corrompue doit être
# détectée comme telle, pas confondue avec une erreur de clé.
info "vérification du manifeste…"
( cd "$SOURCE" && sha256sum --quiet --check <(grep -E '^[0-9a-f]{64} ' manifeste.txt) ) \
  || echec "manifeste invalide : archive corrompue ou incomplète"

# Garde-fou : écraser une base existante doit être explicite.
if psql -X -q -t -A -d postgres -c \
   "select 1 from pg_database where datname='$BASE_CIBLE'" | grep -q 1; then
  if [ "${FORCER:-non}" != "oui" ]; then
    echec "la base $BASE_CIBLE existe déjà. Relancez avec FORCER=oui pour l'écraser."
  fi
  info "suppression de $BASE_CIBLE (FORCER=oui)"
  psql -X -q -d postgres -c "drop database if exists \"$BASE_CIBLE\" with (force)"
fi

psql -X -q -d postgres -c "create database \"$BASE_CIBLE\""

info "déchiffrement et restauration…"
age -d -i "$AGE_IDENTITE" "$SOURCE/base.dump.age" \
  | pg_restore --dbname="$BASE_CIBLE" --no-owner --no-privileges --exit-on-error

vert "Base restaurée : $BASE_CIBLE"

if [ -f "$SOURCE/objets.tar.gz.age" ] && [ -n "${CHEMIN_OBJETS_CIBLE:-}" ]; then
  info "restauration des objets vers $CHEMIN_OBJETS_CIBLE…"
  mkdir -p "$CHEMIN_OBJETS_CIBLE"
  age -d -i "$AGE_IDENTITE" "$SOURCE/objets.tar.gz.age" | tar -C "$CHEMIN_OBJETS_CIBLE" -xzf -
  vert "Objets restaurés"
fi
