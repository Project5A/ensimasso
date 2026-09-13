-- Huit types de blocs sur onze ne pouvaient tout simplement pas être créés.
--
-- La palette du constructeur de pages envoie un payload vide pour tout type :
--
--     apiDashboard.ajouterBloc(jeton(), version.id, t.type, {})
--
-- Or huit schémas sur onze déclarent des propriétés requises. Vérifié en
-- faisant tourner le validateur réel contre le registre réel : COUNTDOWN,
-- EMBED, FAQ, GALLERY, HERO, RICH_TEXT, STATS et TEAM_GRID répondent tous
-- « required property not found », donc 422. Seuls CTA_ADHESION, EVENT_LIST et
-- PARTNERS passaient, parce qu'ils n'exigent rien.
--
-- Autrement dit : la bannière, le texte, le trombinoscope et la galerie — le
-- cœur de « les associations personnalisent leurs pages sans intervention d'un
-- développeur » — étaient inatteignables depuis l'interface. Il n'existe aucun
-- autre chemin : modifier un bloc suppose un bloc déjà créé.
--
-- Le payload de départ appartient au registre, à côté du schéma qu'il doit
-- respecter, et non au code du portail : sinon « ajouter un type de bloc = une
-- ligne ici + un composant React » deviendrait « … + une entrée à ne pas
-- oublier dans un objet du front ». Un test d'intégration valide chaque défaut
-- contre SON schéma : un type ajouté demain sans payload viable ne passe pas.

ALTER TABLE type_bloc
    ADD COLUMN payload_defaut jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE type_bloc SET payload_defaut = '{"titre":"Titre de la bannière"}'::jsonb
WHERE type = 'HERO';

UPDATE type_bloc SET payload_defaut = '{"doc":{"type":"doc","content":[
    {"type":"paragraph","content":[{"type":"text","text":"Votre texte."}]}]}}'::jsonb
WHERE type = 'RICH_TEXT';

UPDATE type_bloc SET payload_defaut = '{"items":[{"libelle":"Adhérents","valeur":"0"}]}'::jsonb
WHERE type = 'STATS';

UPDATE type_bloc SET payload_defaut = '{"source":"MANDAT_DE_LA_PAGE"}'::jsonb
WHERE type = 'TEAM_GRID';

UPDATE type_bloc SET payload_defaut =
    '{"items":[{"question":"Votre question ?","reponse":"Votre réponse."}]}'::jsonb
WHERE type = 'FAQ';

UPDATE type_bloc SET payload_defaut = '{"titre":"Rejoignez-nous"}'::jsonb
WHERE type = 'CTA_ADHESION';

-- La galerie part vide : ses clés de médias viennent du dépôt de fichiers,
-- et inventer une clé inexistante afficherait une image morte.
UPDATE type_bloc SET payload_defaut = '{"mediaKeys":[],"disposition":"GRILLE"}'::jsonb
WHERE type = 'GALLERY';

UPDATE type_bloc SET payload_defaut = '{"style":"CARTES","filtre":"A_VENIR"}'::jsonb
WHERE type = 'EVENT_LIST';

UPDATE type_bloc SET payload_defaut = '{"titre":"Nos partenaires"}'::jsonb
WHERE type = 'PARTNERS';

UPDATE type_bloc SET payload_defaut =
    '{"fournisseur":"YOUTUBE","ref":"identifiant-a-remplacer"}'::jsonb
WHERE type = 'EMBED';

UPDATE type_bloc SET payload_defaut =
    '{"titre":"Compte à rebours","cibleLe":"2030-06-01T20:00:00Z"}'::jsonb
WHERE type = 'COUNTDOWN';

-- Plus de valeur par défaut sur la colonne : un type ajouté demain doit
-- déclarer son payload de départ, et non hériter en silence du « {} » qui
-- était précisément le défaut.
ALTER TABLE type_bloc ALTER COLUMN payload_defaut DROP DEFAULT;

DO $$
DECLARE vides int;
BEGIN
    SELECT count(*) INTO vides FROM type_bloc WHERE payload_defaut = '{}'::jsonb;
    IF vides > 0 THEN
        RAISE EXCEPTION '% type(s) de bloc gardent un payload de départ vide', vides;
    END IF;
END $$;
