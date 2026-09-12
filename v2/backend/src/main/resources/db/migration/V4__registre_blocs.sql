-- =====================================================================
-- V4 — Registre initial des types de blocs.
--
-- Ajouter un type de bloc = une ligne ici + un composant React. Aucune
-- migration de schéma, aucun changement du constructeur : c'est la forme
-- la plus forte que puisse prendre la promesse « les assos personnalisent
-- leurs pages sans intervention d'un développeur ».
--
-- RICH_TEXT stocke un DOCUMENT STRUCTURÉ, jamais une chaîne HTML : il n'y a
-- donc aucun balisage à injecter. JSON Schema ne sait pas exprimer « cette
-- chaîne est du HTML sûr » — on supprime le problème plutôt que de le filtrer.
-- =====================================================================

INSERT INTO type_bloc (type, schema_version, categorie, libelle, composant_react, json_schema) VALUES

('HERO', 1, 'EN_TETE', 'Bannière', 'HeroBlock', '{
  "type":"object","additionalProperties":false,
  "required":["titre"],
  "properties":{
    "titre":{"type":"string","minLength":1,"maxLength":120},
    "sousTitre":{"type":"string","maxLength":300},
    "mediaKey":{"type":"string","maxLength":512},
    "overlay":{"type":"string","enum":["AUCUN","SOMBRE","DEGRADE"]},
    "hauteur":{"type":"string","enum":["COMPACTE","MOYENNE","PLEINE"]},
    "actions":{"type":"array","maxItems":2,"items":{
      "type":"object","additionalProperties":false,
      "required":["libelle","href"],
      "properties":{
        "libelle":{"type":"string","maxLength":40},
        "href":{"type":"string","maxLength":512},
        "style":{"type":"string","enum":["PRIMAIRE","SECONDAIRE"]}}}}}
}'::jsonb),

('RICH_TEXT', 1, 'CONTENU', 'Texte', 'RichTextBlock', '{
  "type":"object","additionalProperties":false,
  "required":["doc"],
  "properties":{
    "doc":{"type":"object","description":"Document structuré (ProseMirror). Jamais du HTML."},
    "largeur":{"type":"string","enum":["ETROITE","NORMALE","LARGE"]}}
}'::jsonb),

('TEAM_GRID', 1, 'CONTENU', 'Trombinoscope', 'TeamGridBlock', '{
  "type":"object","additionalProperties":false,
  "required":["source"],
  "properties":{
    "source":{"type":"string","enum":["MANDAT_DE_LA_PAGE"],
      "description":"Résolu au rendu vers les membres du mandat de CETTE page — jamais le mandat courant, sinon une archive se réécrit toute seule."},
    "postes":{"type":"array","items":{"type":"string"}},
    "colonnes":{"type":"integer","minimum":2,"maximum":4}}
}'::jsonb),

('EVENT_LIST', 1, 'CONTENU', 'Agenda', 'EventListBlock', '{
  "type":"object","additionalProperties":false,
  "properties":{
    "filtre":{"type":"string","enum":["A_VENIR","PASSES","TOUS"]},
    "limite":{"type":"integer","minimum":1,"maximum":24},
    "style":{"type":"string","enum":["LISTE","CARTES","TIMELINE"]}}
}'::jsonb),

('GALLERY', 1, 'MEDIA', 'Galerie', 'GalleryBlock', '{
  "type":"object","additionalProperties":false,
  "required":["mediaKeys"],
  "properties":{
    "mediaKeys":{"type":"array","maxItems":60,"items":{"type":"string","maxLength":512}},
    "disposition":{"type":"string","enum":["MOSAIQUE","CARROUSEL","GRILLE"]}}
}'::jsonb),

('PARTNERS', 1, 'CONTENU', 'Partenaires', 'PartnersBlock', '{
  "type":"object","additionalProperties":false,
  "properties":{
    "titre":{"type":"string","maxLength":120},
    "niveaux":{"type":"array","items":{"type":"string","enum":["OR","ARGENT","BRONZE","SOUTIEN"]}}}
}'::jsonb),

('STATS', 1, 'CONTENU', 'Chiffres clés', 'StatsBlock', '{
  "type":"object","additionalProperties":false,
  "required":["items"],
  "properties":{"items":{"type":"array","minItems":1,"maxItems":6,"items":{
    "type":"object","additionalProperties":false,
    "required":["libelle","valeur"],
    "properties":{
      "libelle":{"type":"string","maxLength":40},
      "valeur":{"type":"string","maxLength":20},
      "icone":{"type":"string","maxLength":40}}}}}
}'::jsonb),

('FAQ', 1, 'CONTENU', 'Questions fréquentes', 'FaqBlock', '{
  "type":"object","additionalProperties":false,
  "required":["items"],
  "properties":{"items":{"type":"array","minItems":1,"maxItems":30,"items":{
    "type":"object","additionalProperties":false,
    "required":["question","reponse"],
    "properties":{
      "question":{"type":"string","maxLength":200},
      "reponse":{"type":"string","maxLength":2000}}}}}
}'::jsonb),

('CTA_ADHESION', 1, 'ACTION', 'Appel à adhérer', 'CtaAdhesionBlock', '{
  "type":"object","additionalProperties":false,
  "properties":{
    "titre":{"type":"string","maxLength":120},
    "texte":{"type":"string","maxLength":400},
    "note":{"type":"string","maxLength":200,
      "description":"Le prix N''est PAS ici : il vient de la campagne, côté serveur."}}
}'::jsonb),

('EMBED', 1, 'MEDIA', 'Intégration', 'EmbedBlock', '{
  "type":"object","additionalProperties":false,
  "required":["fournisseur","ref"],
  "properties":{
    "fournisseur":{"type":"string","enum":["YOUTUBE","INSTAGRAM","SPOTIFY"],
      "description":"Liste blanche : pas d''iframe arbitraire."},
    "ref":{"type":"string","maxLength":200,"pattern":"^[A-Za-z0-9_.:-]+$"}}
}'::jsonb);
