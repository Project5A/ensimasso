-- =====================================================================
-- V7 — Agenda et partenaires.
--
-- Les types de blocs EVENT_LIST et PARTNERS étaient au registre depuis la V4,
-- sans aucune donnée derrière : le constructeur proposait un agenda vide et
-- des partenaires inexistants. On comble le trou plutôt que de retirer les
-- types, parce que ce sont les deux contenus que les assos redemandent en
-- premier après le trombinoscope.
--
-- Les deux tables sont rattachées au MANDAT, pas à l'association, et c'est la
-- décision qui compte : les partenariats se renégocient chaque année, les
-- évènements appartiennent au bureau qui les a organisés. Une convention de
-- 2023 ne doit pas réapparaître d'elle-même sur la page de 2026 — ni l'inverse,
-- une page d'archive ne doit pas perdre son agenda parce que le bureau suivant
-- a fait le ménage.
-- =====================================================================

CREATE TABLE evenement (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mandat_id    uuid NOT NULL REFERENCES mandat(id) ON DELETE CASCADE,
    slug         text NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    titre        text NOT NULL CHECK (length(titre) BETWEEN 1 AND 160),
    resume       text CHECK (resume IS NULL OR length(resume) <= 400),
    description  text,
    lieu         text CHECK (lieu IS NULL OR length(lieu) <= 200),
    debut_le     timestamptz NOT NULL,
    fin_le       timestamptz,
    media_key    text,
    -- Un lien de billetterie externe. Le schéma refuse tout ce qui n'est pas
    -- http(s) : « javascript: » dans un href rendu par le portail est une XSS,
    -- et la liste blanche est portée par la base, pas par le formulaire.
    lien         text CHECK (lien IS NULL OR lien ~ '^https://|^http://'),
    statut       text NOT NULL DEFAULT 'BROUILLON'
                     CHECK (statut IN ('BROUILLON','PUBLIE','ANNULE')),
    complet      boolean NOT NULL DEFAULT false,
    annule_le    timestamptz,
    motif_annulation text CHECK (motif_annulation IS NULL OR length(motif_annulation) <= 400),
    cree_le      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT evenement_periode_ordonnee CHECK (fin_le IS NULL OR fin_le > debut_le),

    -- Un évènement annulé garde sa place et affiche qu'il est annulé. Le faire
    -- disparaître laisserait sans réponse ceux qui ont déjà pris leur billet.
    CONSTRAINT evenement_annulation_coherente
        CHECK (statut <> 'ANNULE' OR annule_le IS NOT NULL),

    -- La clé d'un média est une CLÉ, jamais une URL : même garde-fou que
    -- media_asset, pour que STOR-01 reste impossible à réintroduire ici aussi.
    CONSTRAINT evenement_media_est_une_cle
        CHECK (media_key IS NULL OR (media_key !~ '://' AND media_key !~ '\?')),

    UNIQUE (mandat_id, slug)
);
CREATE INDEX evenement_par_mandat ON evenement (mandat_id, debut_le DESC);

CREATE TABLE partenaire (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mandat_id      uuid NOT NULL REFERENCES mandat(id) ON DELETE CASCADE,
    nom            text NOT NULL CHECK (length(nom) BETWEEN 1 AND 120),
    niveau         text NOT NULL DEFAULT 'SOUTIEN'
                       CHECK (niveau IN ('OR','ARGENT','BRONZE','SOUTIEN')),
    logo_media_key text,
    url            text CHECK (url IS NULL OR url ~ '^https://|^http://'),
    ordre          int  NOT NULL DEFAULT 0,
    visible        boolean NOT NULL DEFAULT true,
    cree_le        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT partenaire_logo_est_une_cle
        CHECK (logo_media_key IS NULL OR (logo_media_key !~ '://' AND logo_media_key !~ '\?')),

    UNIQUE (mandat_id, nom)
);
CREATE INDEX partenaire_par_mandat ON partenaire (mandat_id, niveau, ordre);

-- ------------------------------------------------------- registre de blocs
--
-- COUNTDOWN était cité dans le code du portail sans jamais avoir été déclaré.
-- Il l'est maintenant, et il est délibérément autonome : sa cible est dans son
-- payload, pas une référence à un évènement. Un compte à rebours lié à un
-- évènement supprimé devrait afficher quoi ? La question n'a pas de bonne
-- réponse, donc on ne la pose pas.
INSERT INTO type_bloc (type, schema_version, categorie, libelle, composant_react, json_schema) VALUES
('COUNTDOWN', 1, 'ACTION', 'Compte à rebours', 'CountdownBlock', '{
  "type":"object","additionalProperties":false,
  "required":["titre","cibleLe"],
  "properties":{
    "titre":{"type":"string","minLength":1,"maxLength":120},
    "cibleLe":{"type":"string","format":"date-time",
      "description":"Instant visé, en ISO-8601 avec fuseau."},
    "sousTitre":{"type":"string","maxLength":200},
    "messageApres":{"type":"string","maxLength":200,
      "description":"Affiché une fois la date passée. Sans lui, le bloc afficherait « 0 jour » indéfiniment."},
    "action":{"type":"object","additionalProperties":false,
      "required":["libelle","href"],
      "properties":{
        "libelle":{"type":"string","maxLength":40},
        "href":{"type":"string","maxLength":512}}}}
}'::jsonb);
