-- =====================================================================
-- V1 — Gouvernance : l'ossature temporelle de la plateforme.
--
-- Décision centrale : un mandat est un MANDAT (une période), pas une année.
-- Les élections de bureau ont lieu à l'AG (souvent au printemps), pas le
-- 1er septembre. Clé sur une période + contrainte d'exclusion => impossible
-- d'avoir deux bureaux qui se chevauchent, garanti par la base et non par
-- le code applicatif.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- assos
CREATE TABLE association (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        text NOT NULL UNIQUE
                CHECK (slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    nom         text NOT NULL CHECK (length(btrim(nom)) BETWEEN 1 AND 200),
    type_asso   text NOT NULL CHECK (type_asso IN ('BUREAU','CLUB','TECHNIQUE')),
    fondee_le   date,
    cree_le     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE association IS
  'Identité permanente. Ne change jamais : porte le slug et donc l''URL publique.';

-- --------------------------------------------------------------- années
CREATE TABLE annee_universitaire (
    code    text PRIMARY KEY CHECK (code ~ '^\d{4}-\d{4}$'),  -- '2025-2026'
    debut   date NOT NULL,
    fin     date NOT NULL,
    CHECK (fin > debut)
);
COMMENT ON TABLE annee_universitaire IS
  'Calendrier de référence. Sert d''étiquette : ce n''est PAS la clé du mandat.';

-- -------------------------------------------------------------- mandats
CREATE TABLE mandat (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id  uuid NOT NULL REFERENCES association(id) ON DELETE RESTRICT,
    annee_code      text NOT NULL REFERENCES annee_universitaire(code),

    -- La période est stockée en deux colonnes (JPA sait les mapper) et le
    -- domaine d'intervalle est une colonne GÉNÉRÉE, maintenue par Postgres.
    -- fin_le NULL = mandat en cours dont la fin n'est pas encore fixée.
    debut_le        timestamptz NOT NULL,
    fin_le          timestamptz,
    periode         tstzrange GENERATED ALWAYS AS (tstzrange(debut_le, fin_le)) STORED,

    statut          text NOT NULL
                    CHECK (statut IN ('PREPARATION','EN_FONCTION','CLOS')),
    investi_le      timestamptz,
    clos_le         timestamptz,
    cree_le         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT mandat_periode_ordonnee CHECK (fin_le IS NULL OR fin_le > debut_le),

    -- Deux mandats EN_FONCTION ou CLOS ne peuvent pas se chevaucher pour une
    -- même association. Un mandat en PREPARATION est exclu : sa période est
    -- provisoire tant que la date d'AG n'est pas fixée.
    CONSTRAINT mandat_pas_de_chevauchement
        EXCLUDE USING gist (association_id WITH =, periode WITH &&)
        WHERE (statut <> 'PREPARATION')
);

-- Défense en profondeur : la règle métier « un seul bureau en fonction »
-- énoncée directement, sans dépendre de l'exactitude des périodes.
CREATE UNIQUE INDEX mandat_un_seul_en_fonction
    ON mandat (association_id) WHERE statut = 'EN_FONCTION';

CREATE UNIQUE INDEX mandat_un_seul_par_annee
    ON mandat (association_id, annee_code);

CREATE INDEX mandat_par_asso       ON mandat (association_id);
CREATE INDEX mandat_periode_gist   ON mandat USING gist (periode);

COMMENT ON COLUMN mandat.periode IS
  'Mandat réel du bureau, p.ex. [AG 2026-04-12, AG 2027-04-11). "Qui est le
   bureau maintenant" = periode @> now() — aucun drapeau ACTIF à maintenir.';
COMMENT ON COLUMN mandat.annee_code IS
  'Étiquette lisible ("2026-2027"). N''est PAS la clé : voir periode.';

-- ------------------------------------------------------- membres bureau
CREATE TABLE membre_bureau (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mandat_id       uuid NOT NULL REFERENCES mandat(id) ON DELETE CASCADE,
    personne_id     uuid NOT NULL,               -- « sub » Keycloak
    poste           text NOT NULL CHECK (poste IN (
                        'PRESIDENT','VICE_PRESIDENT','TRESORIER','SECRETAIRE',
                        'RESP_COM','RESP_EVENEMENTS','MEMBRE_BUREAU')),
    titre_affiche   text,                        -- certaines assos ont leurs titres
    ordre           int  NOT NULL DEFAULT 0,
    photo_media_key text,                        -- CLÉ d'objet, jamais une URL signée
    visible_public  boolean NOT NULL DEFAULT true,
    nomme_le        timestamptz NOT NULL DEFAULT now(),
    revoque_le      timestamptz,

    CONSTRAINT membre_revocation_coherente
        CHECK (revoque_le IS NULL OR revoque_le >= nomme_le)
);

-- Une personne n'occupe qu'un poste actif à la fois dans un mandat donné.
CREATE UNIQUE INDEX membre_un_poste_actif_par_personne
    ON membre_bureau (mandat_id, personne_id) WHERE revoque_le IS NULL;

-- Les postes statutaires sont uniques dans un bureau.
CREATE UNIQUE INDEX membre_postes_statutaires_uniques
    ON membre_bureau (mandat_id, poste)
    WHERE revoque_le IS NULL
      AND poste IN ('PRESIDENT','VICE_PRESIDENT','TRESORIER','SECRETAIRE');

CREATE INDEX membre_par_personne ON membre_bureau (personne_id) WHERE revoque_le IS NULL;
CREATE INDEX membre_par_mandat   ON membre_bureau (mandat_id);

-- ------------------------------------------------------------ passation
CREATE TABLE passation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id      uuid NOT NULL REFERENCES association(id) ON DELETE CASCADE,
    mandat_sortant_id   uuid REFERENCES mandat(id),
    mandat_entrant_id   uuid NOT NULL REFERENCES mandat(id),
    statut              text NOT NULL CHECK (statut IN
                            ('PREPAREE','BUREAU_COMPLETE','ACTIVEE','ANNULEE')),
    preparee_le         timestamptz NOT NULL DEFAULT now(),
    preparee_par        uuid NOT NULL,
    activee_le          timestamptz,
    pages_clonees       int NOT NULL DEFAULT 0,

    CONSTRAINT passation_mandats_distincts
        CHECK (mandat_sortant_id IS NULL OR mandat_sortant_id <> mandat_entrant_id)
);

-- Une seule passation ouverte par association.
CREATE UNIQUE INDEX passation_une_seule_ouverte
    ON passation (association_id)
    WHERE statut IN ('PREPAREE','BUREAU_COMPLETE');

CREATE UNIQUE INDEX passation_par_mandat_entrant ON passation (mandat_entrant_id);
