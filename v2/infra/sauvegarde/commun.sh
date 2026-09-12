#!/usr/bin/env bash
# Fonctions partagées par les scripts de sauvegarde.
set -euo pipefail
IFS=$'\n\t'

# --- configuration, entièrement par variables d'environnement -------------
: "${PGHOST:=localhost}"
: "${PGPORT:=5432}"
: "${PGUSER:=ensimasso}"
: "${PGDATABASE:=ensimasso}"
: "${DEPOT:=/var/backups/ensimasso}"
# Exportées explicitement : psql, pg_dump et pg_restore les lisent dans
# l'environnement, pas dans les variables du shell appelant.
export PGHOST PGPORT PGUSER PGDATABASE

# Clé PUBLIQUE age. L'hôte de sauvegarde ne détient que celle-ci : il peut
# écrire des sauvegardes, il ne peut pas lire les anciennes. Un hôte compromis
# ne donne donc pas accès à l'historique — c'est la raison du chiffrement
# asymétrique plutôt qu'une phrase de passe partagée.
: "${AGE_DESTINATAIRE:=}"

# Clé PRIVÉE, présente uniquement là où l'on restaure. Jamais sur l'hôte
# sauvegardé, jamais dans le dépôt git, jamais dans une image.
: "${AGE_IDENTITE:=}"

: "${RETENTION_QUOTIDIENNE:=7}"
: "${RETENTION_HEBDO:=4}"
: "${RETENTION_MENSUELLE:=3}"

rouge()  { printf '\033[31m%s\033[0m\n' "$*" >&2; }
vert()   { printf '\033[32m%s\033[0m\n' "$*"; }
info()   { printf '  %s\n' "$*"; }
titre()  { printf '\n== %s ==\n' "$*"; }

echec() { rouge "ERREUR: $*"; exit 1; }

exiger_outil() {
  command -v "$1" >/dev/null 2>&1 || echec "outil requis absent : $1"
}

# Empreinte déterministe du CONTENU d'une base, table par table.
#
# Compte les lignes ET hache le contenu, trié, pour que l'ordre physique
# n'influe pas. C'est ce qui distingue « pg_restore est sorti en 0 » de
# « les données sont réellement identiques » — et la différence entre avoir
# des sauvegardes et avoir une restauration.
empreinte_base() {
  local base="$1"
  psql -X -q -t -A -d "$base" <<'SQL'
SELECT string_agg(ligne, E'\n' ORDER BY ligne) FROM (
  SELECT format('%s|%s|%s',
           c.relname,
           (xpath('/row/c/text()',
                  query_to_xml(format('SELECT count(*) AS c FROM public.%I', c.relname),
                               false, true, '')))[1]::text,
           COALESCE((xpath('/row/c/text()',
                  query_to_xml(format(
                    'SELECT md5(COALESCE(string_agg(t::text, %L ORDER BY t::text), %L)) AS c FROM public.%I t',
                    '', '', c.relname),
                    false, true, '')))[1]::text, 'vide')
         ) AS ligne
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public' AND c.relkind = 'r'
) s;
SQL
}

# Empreinte du SCHÉMA : contraintes, index, déclencheurs, fonctions.
#
# Comparer les lignes ne suffit pas. Toute l'architecture v2 repose sur des
# invariants tenus par la base elle-même (EXCLUDE sur les mandats, trigger
# différé sur les totaux de commande, index uniques partiels). Une restauration
# qui ramènerait les données sans les contraintes rendrait la base corruptible
# sans que rien ne le signale : c'est exactement le genre de panne qu'on ne
# découvre qu'au moment où il est trop tard.
empreinte_schema() {
  local base="$1"
  psql -X -q -t -A -d "$base" <<'SQL'
SELECT string_agg(ligne, E'\n' ORDER BY ligne) FROM (
  SELECT format('contrainte|%s|%s|%s',
                c.conrelid::regclass::text, c.conname, pg_get_constraintdef(c.oid)) AS ligne
    FROM pg_constraint c
    JOIN pg_namespace n ON n.oid = c.connamespace
   WHERE n.nspname = 'public'
  UNION ALL
  SELECT format('index|%s|%s', i.tablename, i.indexdef)
    FROM pg_indexes i WHERE i.schemaname = 'public'
  UNION ALL
  SELECT format('declencheur|%s|%s|%s',
                t.tgrelid::regclass::text, t.tgname, pg_get_triggerdef(t.oid))
    FROM pg_trigger t
    JOIN pg_class c2 ON c2.oid = t.tgrelid
    JOIN pg_namespace n2 ON n2.oid = c2.relnamespace
   WHERE n2.nspname = 'public' AND NOT t.tgisinternal
  UNION ALL
  SELECT format('fonction|%s|%s', p.proname, md5(pg_get_functiondef(p.oid)))
    FROM pg_proc p
    JOIN pg_namespace n3 ON n3.oid = p.pronamespace
   WHERE n3.nspname = 'public' AND p.prokind = 'f'
) s;
SQL
}
