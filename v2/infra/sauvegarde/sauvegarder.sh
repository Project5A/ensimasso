#!/usr/bin/env bash
#
# Sauvegarde chiffrée de la base et du stockage objet.
#
# Produit, pour chaque exécution :
#   <horodatage>/base.dump.age     — pg_dump format custom, chiffré
#   <horodatage>/objets.tar.gz.age — volume des médias, chiffré
#   <horodatage>/manifeste.txt     — tailles et empreintes SHA-256
#
# Le manifeste n'est PAS chiffré : on doit pouvoir vérifier l'intégrité d'une
# archive sans détenir la clé privée, donc sans la déchiffrer.
set -euo pipefail
IFS=$'\n\t'
cd "$(dirname "$0")"
# shellcheck source=commun.sh
source ./commun.sh

exiger_outil pg_dump
exiger_outil age
exiger_outil sha256sum

[ -n "$AGE_DESTINATAIRE" ] || echec "AGE_DESTINATAIRE (clé publique age) est requis"

HORODATAGE="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
CIBLE="$DEPOT/$HORODATAGE"
mkdir -p "$CIBLE"

titre "Sauvegarde $HORODATAGE"
info "base      : $PGUSER@$PGHOST:$PGPORT/$PGDATABASE"
info "dépôt     : $CIBLE"

# --- base -----------------------------------------------------------------
# Format « custom » : compressé, et restaurable table par table, ce qui permet
# une restauration partielle sans rejouer tout le dump.
info "pg_dump…"
pg_dump --format=custom --no-owner --no-privileges --compress=6 \
  | age -r "$AGE_DESTINATAIRE" -o "$CIBLE/base.dump.age"

# --- objets ---------------------------------------------------------------
# Les médias sont hors base (clés d'objet en base, octets dans le stockage) :
# une sauvegarde de la base seule laisserait des pages d'archive sans images.
if [ -n "${CHEMIN_OBJETS:-}" ] && [ -d "${CHEMIN_OBJETS:-}" ]; then
  info "archivage des objets ($CHEMIN_OBJETS)…"
  tar -C "$CHEMIN_OBJETS" -czf - . | age -r "$AGE_DESTINATAIRE" -o "$CIBLE/objets.tar.gz.age"
else
  info "CHEMIN_OBJETS non défini ou absent : objets NON sauvegardés"
fi

# --- manifeste ------------------------------------------------------------
{
  echo "horodatage=$HORODATAGE"
  echo "base=$PGDATABASE"
  echo "hote=$PGHOST"
  echo "version_pg=$(psql -X -t -A -c 'show server_version')"
  echo "---"
  ( cd "$CIBLE" && sha256sum ./*.age )
} > "$CIBLE/manifeste.txt"

# --- rétention ------------------------------------------------------------
# On garde les N plus récentes par jour, puis une par semaine, puis une par
# mois. Volontairement simple : une rétention que personne ne comprend est une
# rétention que personne ne vérifie.
appliquer_retention() {
  local -a toutes
  mapfile -t toutes < <(find "$DEPOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r)
  local gardees=() vues_semaines=() vues_mois=()
  local i=0
  for d in "${toutes[@]}"; do
    local semaine mois
    semaine="$(date -u -d "${d%%T*}" +%G-W%V 2>/dev/null || echo "?")"
    mois="${d:0:7}"
    if   [ "$i" -lt "$RETENTION_QUOTIDIENNE" ]; then gardees+=("$d")
    elif [ "${#vues_semaines[@]}" -lt "$RETENTION_HEBDO" ] \
         && [[ ! " ${vues_semaines[*]:-} " == *" $semaine "* ]]; then
      gardees+=("$d"); vues_semaines+=("$semaine")
    elif [ "${#vues_mois[@]}" -lt "$RETENTION_MENSUELLE" ] \
         && [[ ! " ${vues_mois[*]:-} " == *" $mois "* ]]; then
      gardees+=("$d"); vues_mois+=("$mois")
    fi
    i=$((i + 1))
  done

  for d in "${toutes[@]}"; do
    if [[ ! " ${gardees[*]} " == *" $d "* ]]; then
      info "purge $d"
      rm -rf "${DEPOT:?}/${d:?}"
    fi
  done
}
appliquer_retention

TAILLE="$(du -sh "$CIBLE" | cut -f1)"
vert "Sauvegarde terminée : $CIBLE ($TAILLE)"

# Rappel affiché à chaque exécution, délibérément : une sauvegarde qui reste
# sur la machine sauvegardée ne protège d'à peu près rien.
if [ -z "${DESTINATION_DISTANTE:-}" ]; then
  rouge "DESTINATION_DISTANTE non définie : la sauvegarde reste SUR L'HÔTE."
  rouge "Elle ne protège ni d'une perte de disque, ni d'une compromission de la machine."
fi
