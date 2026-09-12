-- =====================================================================
-- V2 — Contenu : le constructeur de pages.
--
-- Quatre garanties que la revue a signalées comme reposant, dans la V1 de
-- cette conception, sur une *convention* (« le code ne fait pas d'UPDATE »)
-- plutôt que sur une *propriété*. Toutes sont désormais imposées par la base.
-- =====================================================================

-- ------------------------------------------------ registre des blocs
-- Versionné, jamais muté sur place : sans cela on ne peut plus revalider
-- un bloc de 2025, et un « upcaster » n'a aucun contrat source à vérifier.
CREATE TABLE type_bloc (
    type            text NOT NULL,
    schema_version  int  NOT NULL CHECK (schema_version >= 1),
    json_schema     jsonb NOT NULL,
    composant_react text NOT NULL,
    categorie       text NOT NULL,
    libelle         text NOT NULL,
    depreciee       boolean NOT NULL DEFAULT false,
    cree_le         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (type, schema_version)
);
COMMENT ON TABLE type_bloc IS
  'Registre des types de blocs. PK composite (type, schema_version) : une
   nouvelle version ajoute une ligne, elle n''écrase jamais la précédente.';

-- -------------------------------------------------------------- pages
CREATE TABLE page (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mandat_id   uuid NOT NULL REFERENCES mandat(id) ON DELETE CASCADE,
    slug        text NOT NULL CHECK (slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    titre       text NOT NULL,
    ordre_menu  int  NOT NULL DEFAULT 0,
    cree_le     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (mandat_id, slug)
);

CREATE TABLE page_version (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id     uuid NOT NULL REFERENCES page(id) ON DELETE CASCADE,
    numero      int  NOT NULL CHECK (numero >= 1),
    statut      text NOT NULL CHECK (statut IN ('BROUILLON','PUBLIEE','ARCHIVEE')),
    cree_par    uuid NOT NULL,
    cree_le     timestamptz NOT NULL DEFAULT now(),
    publie_par  uuid,
    publie_le   timestamptz,
    note        text,
    UNIQUE (page_id, numero),
    CONSTRAINT version_publication_coherente
        CHECK ((statut = 'BROUILLON' AND publie_le IS NULL)
            OR (statut IN ('PUBLIEE','ARCHIVEE') AND publie_le IS NOT NULL))
);

-- GARANTIE 1 — au plus une version publiée par page.
-- Sans cela, un crash entre deux écritures laisse deux lignes PUBLIEE et le
-- site public en choisit une arbitrairement.
CREATE UNIQUE INDEX page_version_une_seule_publiee
    ON page_version (page_id) WHERE statut = 'PUBLIEE';

CREATE INDEX page_version_par_page ON page_version (page_id);

-- -------------------------------------------------------------- blocs
CREATE TABLE bloc (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    page_version_id uuid NOT NULL REFERENCES page_version(id) ON DELETE CASCADE,
    ordre           int  NOT NULL CHECK (ordre >= 0),
    type            text NOT NULL,
    schema_version  int  NOT NULL,
    payload         jsonb NOT NULL,
    visible         boolean NOT NULL DEFAULT true,

    -- GARANTIE 3 — un bloc pointe vers une version PRÉCISE du schéma, qui
    -- existe toujours. Un bloc archivé reste donc revalidable en 2031.
    CONSTRAINT bloc_type_versionne
        FOREIGN KEY (type, schema_version) REFERENCES type_bloc (type, schema_version),

    CONSTRAINT bloc_ordre_unique UNIQUE (page_version_id, ordre) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX bloc_par_version ON bloc (page_version_id, ordre);

-- GARANTIE 2 — le contenu publié est immuable.
-- Dix lignes qui transforment l'affirmation centrale du système (« l'archive
-- 2023-2024 ne peut pas être réécrite ») d'une promesse en une propriété.
CREATE FUNCTION contenu_refuse_modif_figee() RETURNS trigger AS $$
DECLARE
    v_version_id uuid;
    v_statut     text;
BEGIN
    v_version_id := COALESCE(NEW.page_version_id, OLD.page_version_id);
    SELECT statut INTO v_statut FROM page_version WHERE id = v_version_id;

    IF v_statut IN ('PUBLIEE','ARCHIVEE') THEN
        RAISE EXCEPTION
            'bloc figé : la version % est % et ne peut plus être modifiée',
            v_version_id, v_statut
            USING ERRCODE = '23514';   -- check_violation
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;

CREATE TRIGGER bloc_fige
    BEFORE INSERT OR UPDATE OR DELETE ON bloc
    FOR EACH ROW EXECUTE FUNCTION contenu_refuse_modif_figee();

-- Transitions de statut autorisées uniquement : BROUILLON -> PUBLIEE -> ARCHIVEE.
-- Interdit notamment de « dépublier » en repassant à BROUILLON, ce qui
-- rendrait les blocs à nouveau modifiables et réécrirait l'histoire.
CREATE FUNCTION contenu_transition_version() RETURNS trigger AS $$
BEGIN
    IF OLD.statut = NEW.statut THEN
        RETURN NEW;
    END IF;
    IF (OLD.statut = 'BROUILLON' AND NEW.statut = 'PUBLIEE')
       OR (OLD.statut = 'PUBLIEE' AND NEW.statut = 'ARCHIVEE') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'transition de version interdite : % -> %', OLD.statut, NEW.statut
        USING ERRCODE = '23514';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER page_version_transition
    BEFORE UPDATE OF statut ON page_version
    FOR EACH ROW EXECUTE FUNCTION contenu_transition_version();

-- -------------------------------------------------------------- thème
CREATE TABLE theme_version (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mandat_id   uuid NOT NULL REFERENCES mandat(id) ON DELETE CASCADE,
    numero      int  NOT NULL CHECK (numero >= 1),
    statut      text NOT NULL CHECK (statut IN ('BROUILLON','PUBLIEE','ARCHIVEE')),
    tokens      jsonb NOT NULL,
    cree_le     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (mandat_id, numero)
);
CREATE UNIQUE INDEX theme_une_seule_publiee
    ON theme_version (mandat_id) WHERE statut = 'PUBLIEE';

-- ------------------------------------------------- utilisation médias
-- Les media_key vivent dans du JSONB : aucune intégrité référentielle
-- possible. Cette table répond à « où cette image est-elle utilisée ? »
-- et permet de refuser la suppression d'un média référencé par une
-- version publiée ou archivée.
CREATE TABLE media_usage (
    media_key       text NOT NULL,
    page_version_id uuid NOT NULL REFERENCES page_version(id) ON DELETE CASCADE,
    PRIMARY KEY (media_key, page_version_id)
);
CREATE INDEX media_usage_par_cle ON media_usage (media_key);
