-- =====================================================================
-- V5 — Médiathèque.
--
-- Corrige STOR-01, le bug le plus insidieux de la v1 : l'upload générait une
-- URL SAS valable 24 h et c'est cette URL COMPLÈTE, jeton compris, qui était
-- écrite en base comme photo de profil ou image de galerie. Rien ne la
-- régénérait. Toutes les images de la plateforme meurent donc un jour après
-- leur dépôt, silencieusement.
--
-- Ici la base ne stocke QUE la clé d'objet. L'URL est fabriquée à la lecture.
-- =====================================================================

CREATE TABLE media_asset (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id    uuid NOT NULL REFERENCES association(id) ON DELETE RESTRICT,
    annee_code        text NOT NULL REFERENCES annee_universitaire(code),

    -- La clé, et rien que la clé : {slug}/{annee}/{uuid}.{ext}
    -- Jamais d'URL, jamais de jeton, jamais de nom d'hôte — sans quoi
    -- déplacer le domaine ou ajouter un CDN casse six ans d'archives.
    cle               text NOT NULL UNIQUE
                      CHECK (cle !~ '^https?://' AND cle !~ '\?'),

    nom_original      text,
    content_type      text NOT NULL,
    taille_octets     bigint CHECK (taille_octets IS NULL OR taille_octets >= 0),
    largeur           int,
    hauteur           int,
    blurhash          text,
    texte_alternatif  text,

    statut            text NOT NULL CHECK (statut IN
                          ('ATTENTE_DEPOT','DISPONIBLE','REJETE','SUPPRIME')),
    depose_par        uuid NOT NULL,
    cree_le           timestamptz NOT NULL DEFAULT now(),
    confirme_le       timestamptz,

    CONSTRAINT media_confirmation_coherente
        CHECK (statut <> 'DISPONIBLE' OR confirme_le IS NOT NULL)
);

CREATE INDEX media_par_asso_annee ON media_asset (association_id, annee_code);
CREATE INDEX media_disponibles    ON media_asset (association_id) WHERE statut = 'DISPONIBLE';

COMMENT ON COLUMN media_asset.cle IS
  'Clé d''objet dans le stockage. La contrainte CHECK refuse explicitement une
   URL ou une chaîne de requête : c''est le bug STOR-01 de la v1 rendu
   impossible à réintroduire.';

-- Variantes dérivées (vignettes, WebP, AVIF), produites par le worker média.
CREATE TABLE media_variante (
    media_id    uuid NOT NULL REFERENCES media_asset(id) ON DELETE CASCADE,
    format      text NOT NULL CHECK (format IN ('WEBP','AVIF','JPEG')),
    largeur     int  NOT NULL CHECK (largeur > 0),
    cle         text NOT NULL UNIQUE CHECK (cle !~ '^https?://'),
    taille_octets bigint,
    PRIMARY KEY (media_id, format, largeur)
);

-- L'intégrité que le JSONB ne peut pas donner.
-- media_usage est écrite à la publication ; cette clé étrangère garantit
-- qu'elle ne peut pas désigner un média qui n'existe pas, et le service
-- refuse de supprimer un média référencé par une version figée.
ALTER TABLE media_usage
    ADD CONSTRAINT media_usage_reference_un_media
    FOREIGN KEY (media_key) REFERENCES media_asset(cle) ON DELETE RESTRICT;
