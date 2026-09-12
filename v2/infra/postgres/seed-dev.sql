-- Jeu de données de développement : trois associations réelles de l'ENSIM,
-- réparties sur deux années, avec une passation de printemps déjà jouée.
--
-- L'objectif est que la base locale ressemble à la réalité : sans cela on
-- développe contre un cas vide et on découvre le cas « archive » en production.

BEGIN;

-- Ce script cible une base FRAÎCHEMENT MIGRÉE. Il n'est volontairement pas
-- idempotent : le trigger d'immuabilité interdit de supprimer les blocs d'une
-- version publiée, et c'est exactement la garantie qu'on veut. Rejouer le seed
-- sur une base peuplée doit donc échouer bruyamment, pas à moitié.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM association) THEN
        RAISE EXCEPTION
            'la base contient déjà des données. Lancez « make reset » pour repartir vierge, '
            'puis « make seed ». (Le contenu publié est immuable : il ne peut pas être écrasé.)';
    END IF;
END $$;

INSERT INTO annee_universitaire (code, debut, fin) VALUES
    ('2024-2025','2024-09-01','2025-08-31'),
    ('2025-2026','2025-09-01','2026-08-31'),
    ('2026-2027','2026-09-01','2027-08-31')
;

INSERT INTO association (id, slug, nom, type_asso, fondee_le) VALUES
    ('11111111-1111-1111-1111-111111111111','bde','Bureau des Élèves','BUREAU','1988-10-01'),
    ('22222222-2222-2222-2222-222222222222','bds','Bureau des Sports','BUREAU','1992-09-15'),
    ('33333333-3333-3333-3333-333333333333','kfet','K-Fêt','CLUB','1995-03-01')
;

-- BDE : mandat 2024-2025 CLOS (élu en avril 2024), puis 2025-2026 EN_FONCTION.
-- C'est exactement le scénario de passation de printemps que le modèle par
-- année était incapable de représenter.
INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut, investi_le, clos_le) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111',
     '2024-2025','2024-04-11 18:00+00','2025-04-10 18:00+00','CLOS',
     '2024-04-11 18:00+00','2025-04-10 18:00+00')
;

INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut, investi_le) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111',
     '2025-2026','2025-04-10 18:00+00', NULL,'EN_FONCTION','2025-04-10 18:00+00'),
    ('bbbbbbbb-0000-0000-0000-000000000001','22222222-2222-2222-2222-222222222222',
     '2025-2026','2025-09-01 12:00+00', NULL,'EN_FONCTION','2025-09-01 12:00+00'),
    ('cccccccc-0000-0000-0000-000000000001','33333333-3333-3333-3333-333333333333',
     '2025-2026','2025-09-15 12:00+00', NULL,'EN_FONCTION','2025-09-15 12:00+00')
;

-- Bureaux. Les identifiants de personne correspondent aux « sub » des
-- utilisateurs importés dans le royaume Keycloak de développement.
INSERT INTO membre_bureau (mandat_id, personne_id, poste, ordre, titre_affiche) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000001','PRESIDENT',0,'Présidente'),
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000002','TRESORIER',1,NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000003','RESP_COM',2,'Resp. Communication'),
    ('aaaaaaaa-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000009','PRESIDENT',0,NULL),
    ('bbbbbbbb-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000004','PRESIDENT',0,NULL),
    ('cccccccc-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000005','PRESIDENT',0,NULL)
;

-- Une page d'accueil publiée pour le BDE, sur le mandat en cours.
--
-- Noter l'ordre : la version est créée en BROUILLON, les blocs sont insérés,
-- PUIS la version est publiée. Le trigger d'immuabilité refuse d'ajouter un
-- bloc à une version déjà publiée — il a d'ailleurs attrapé une première
-- version de ce script qui s'y prenait à l'envers.
INSERT INTO page (id, mandat_id, slug, titre, ordre_menu) VALUES
    ('eeeeeeee-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002','accueil','Accueil',0),
    ('eeeeeeee-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000002','equipe','L''équipe',1)
;

INSERT INTO page_version (id, page_id, numero, statut, cree_par) VALUES
    ('ffffffff-0000-0000-0000-000000000001','eeeeeeee-0000-0000-0000-000000000001',1,'BROUILLON',
     'd0000000-0000-4000-8000-000000000001'),
    ('ffffffff-0000-0000-0000-000000000002','eeeeeeee-0000-0000-0000-000000000002',1,'BROUILLON',
     'd0000000-0000-4000-8000-000000000001')
;

INSERT INTO bloc (page_version_id, ordre, type, schema_version, payload) VALUES
    ('ffffffff-0000-0000-0000-000000000001',0,'HERO',1,
     '{"titre":"Bureau des Élèves","sousTitre":"La vie étudiante à l''ENSIM, depuis 1988","hauteur":"MOYENNE","overlay":"DEGRADE","actions":[{"libelle":"Adhérer","href":"/adherer","style":"PRIMAIRE"},{"libelle":"Nos évènements","href":"/evenements","style":"SECONDAIRE"}]}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',1,'COUNTDOWN',1,
     jsonb_build_object(
       'titre','Avant le Gala de printemps',
       'cibleLe', to_jsonb(date_trunc('day', now() + interval '3 weeks') + interval '20 hours'),
       'sousTitre','Billetterie ouverte aux adhérents',
       'messageApres','Le Gala a eu lieu. Merci aux 300 personnes présentes !',
       'action', jsonb_build_object('libelle','Billetterie','href','/evenements/gala-de-printemps'))),
    ('ffffffff-0000-0000-0000-000000000001',2,'STATS',1,
     '{"items":[{"libelle":"Adhérents","valeur":"420"},{"libelle":"Évènements par an","valeur":"35"},{"libelle":"Clubs affiliés","valeur":"12"}]}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',3,'RICH_TEXT',1,
     '{"largeur":"NORMALE","doc":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Le BDE fédère la vie associative de l''école : évènements, clubs, partenariats et représentation des étudiants auprès de l''administration."}]},{"type":"paragraph","content":[{"type":"text","text":"Adhérer donne accès aux tarifs réduits sur tous les évènements de l''année, à la K-Fêt et aux sorties organisées par les clubs."}]}]}}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',4,'TEAM_GRID',1,
     '{"source":"MANDAT_DE_LA_PAGE","colonnes":3}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',5,'EVENT_LIST',1,
     '{"filtre":"A_VENIR","limite":4,"style":"CARTES"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',6,'PARTNERS',1,
     '{"titre":"Ils nous soutiennent cette année"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',7,'FAQ',1,
     '{"items":[{"question":"Comment adhérer ?","reponse":"En ligne depuis cette page, ou au local du BDE pendant les permanences."},{"question":"L''adhésion est-elle valable toute l''année ?","reponse":"Elle couvre l''année universitaire en cours, de septembre à août."},{"question":"Je suis en échange, puis-je adhérer ?","reponse":"Oui, au tarif étudiant, sur présentation de votre certificat de scolarité."}]}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',8,'CTA_ADHESION',1,
     '{"titre":"Rejoignez le BDE","texte":"Une adhésion, toute l''année, et tous les évènements à tarif réduit.","note":"Le tarif est affiché à l''étape suivante."}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000002',0,'HERO',1,
     '{"titre":"L''équipe 2025-2026","sousTitre":"Le bureau élu à l''assemblée générale d''avril 2025","hauteur":"COMPACTE","overlay":"SOMBRE"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000002',1,'TEAM_GRID',1,
     '{"source":"MANDAT_DE_LA_PAGE","colonnes":3}'::jsonb)
;

-- Publication : c'est seulement ici que le contenu se fige.
UPDATE page_version
   SET statut = 'PUBLIEE', publie_le = now(), publie_par = 'd0000000-0000-4000-8000-000000000001'
 WHERE id IN ('ffffffff-0000-0000-0000-000000000001','ffffffff-0000-0000-0000-000000000002');

-- Thème : c'est ce qui fait qu'une asso ne ressemble pas à une autre, sans code.
INSERT INTO theme_version (id, mandat_id, numero, statut, tokens) VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002',1,'PUBLIEE',
     '{"accent":"#1F4E79","accentContraste":"#FFFFFF","encre":"#101820","fond":"#F6F7F9","police":"SANS","rayon":"8px"}'::jsonb),
    ('a1a1a1a1-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000001',1,'PUBLIEE',
     '{"accent":"#146B4A","accentContraste":"#FFFFFF","encre":"#0E1A16","fond":"#F4F8F6","police":"SANS","rayon":"14px"}'::jsonb),
    ('a1a1a1a1-0000-0000-0000-000000000003','cccccccc-0000-0000-0000-000000000001',1,'PUBLIEE',
     '{"accent":"#A8431F","accentContraste":"#FFFFFF","encre":"#1C120E","fond":"#FAF6F3","police":"SERIF","rayon":"4px"}'::jsonb)
;

-- Une page d'ARCHIVE : le mandat 2024-2025, clos. Elle doit afficher le bureau
-- de 2024-2025 pour toujours, pas celui en fonction.
INSERT INTO page (id, mandat_id, slug, titre, ordre_menu) VALUES
    ('eeeeeeee-0000-0000-0000-000000000009','aaaaaaaa-0000-0000-0000-000000000001','accueil','Accueil',0)
;
INSERT INTO page_version (id, page_id, numero, statut, cree_par) VALUES
    ('ffffffff-0000-0000-0000-000000000009','eeeeeeee-0000-0000-0000-000000000009',1,'BROUILLON',
     'd0000000-0000-4000-8000-000000000009')
;
INSERT INTO bloc (page_version_id, ordre, type, schema_version, payload) VALUES
    ('ffffffff-0000-0000-0000-000000000009',0,'HERO',1,
     '{"titre":"Bureau des Élèves","sousTitre":"Mandat 2024-2025 — archive","hauteur":"COMPACTE","overlay":"SOMBRE"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000009',1,'TEAM_GRID',1,
     '{"source":"MANDAT_DE_LA_PAGE","colonnes":3}'::jsonb),
    -- Le MÊME bloc « à venir » que sur la page en cours. Sur une archive il
    -- affiche le bilan du mandat : un mandat terminé n'a rien à venir, et une
    -- page d'archive à l'agenda vide laisserait croire que ce bureau n'a rien
    -- organisé.
    ('ffffffff-0000-0000-0000-000000000009',2,'EVENT_LIST',1,
     '{"filtre":"A_VENIR","limite":10,"style":"LISTE"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000009',3,'PARTNERS',1,
     '{"titre":"Nos partenaires en 2024-2025"}'::jsonb)
;
UPDATE page_version
   SET statut = 'PUBLIEE', publie_le = now(), publie_par = 'd0000000-0000-4000-8000-000000000009'
 WHERE id = 'ffffffff-0000-0000-0000-000000000009';

INSERT INTO theme_version (id, mandat_id, numero, statut, tokens) VALUES
    ('a1a1a1a1-0000-0000-0000-000000000009','aaaaaaaa-0000-0000-0000-000000000001',1,'PUBLIEE',
     '{"accent":"#5B4B8A","accentContraste":"#FFFFFF","encre":"#171426","fond":"#F7F6FA","police":"SANS","rayon":"8px"}'::jsonb)
;

-- ------------------------------------------------------------ agenda
--
-- Les évènements appartiennent au MANDAT qui les organise. Les dates du mandat
-- en cours sont relatives à « maintenant » pour que le jeu de données ne
-- pourrisse pas : un agenda de démonstration entièrement passé ne démontre
-- plus rien six mois après avoir été écrit.
-- Noter la colonne annule_le : une contrainte CHECK refuse un évènement
-- ANNULE sans date d'annulation, et elle a attrapé la première version de
-- ce script. Un statut « annulé » sans trace de quand ne vaut rien pour
-- quelqu'un qui cherche à savoir s'il a manqué l'information.
INSERT INTO evenement (mandat_id, slug, titre, resume, lieu, debut_le, fin_le, statut,
                       lien, complet, annule_le, motif_annulation) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','gala-de-printemps','Gala de printemps',
     'La soirée de l''année, en tenue de soirée, au Palais des Congrès.',
     'Palais des Congrès, Le Mans',
     date_trunc('day', now() + interval '3 weeks') + interval '20 hours',
     date_trunc('day', now() + interval '3 weeks') + interval '26 hours', 'PUBLIE',
     'https://billetterie.example.org/gala', false, NULL, NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002','week-end-integration','Week-end d''intégration',
     'Deux jours pour que la promo entrante rencontre le reste de l''école.',
     'Base de loisirs de la Gèsière',
     date_trunc('day', now() + interval '8 weeks') + interval '9 hours',
     date_trunc('day', now() + interval '8 weeks 2 days') + interval '17 hours', 'PUBLIE', NULL, true, NULL, NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002','tournoi-e-sport','Tournoi e-sport',
     'Annulé faute de salle : la réservation de l''amphi a été reprise par l''administration.',
     'Amphi A',
     date_trunc('day', now() + interval '2 weeks') + interval '14 hours', NULL, 'ANNULE', NULL, false,
     now() - interval '2 days', 'Amphi repris par l''administration pour un jury.'),
    ('aaaaaaaa-0000-0000-0000-000000000002','afterwork-rentree','Afterwork de rentrée',
     'Le premier rendez-vous de l''année, à la K-Fêt.', 'K-Fêt',
     date_trunc('day', now() - interval '5 weeks') + interval '18 hours',
     date_trunc('day', now() - interval '5 weeks') + interval '22 hours',
     'PUBLIE', NULL, false, NULL, NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002','brouillon-ski','Séjour ski',
     'Encore en préparation : ni dates ni budget arrêtés.', 'Alpes',
     date_trunc('day', now() + interval '20 weeks') + interval '8 hours', NULL, 'BROUILLON', NULL, false, NULL, NULL),

    -- Le mandat clos garde les siens, pour toujours.
    ('aaaaaaaa-0000-0000-0000-000000000001','gala-2024','Gala 2024',
     'L''édition précédente, au Théâtre des Quinconces.', 'Théâtre des Quinconces',
     '2024-11-23 20:00+00','2024-11-24 03:00+00','PUBLIE', NULL, false, NULL, NULL),
    ('aaaaaaaa-0000-0000-0000-000000000001','telethon-2024','Téléthon',
     '24 heures de défis sportifs au profit du Téléthon.', 'Gymnase de l''ENSIM',
     '2024-12-06 08:00+00','2024-12-07 08:00+00','PUBLIE', NULL, false, NULL, NULL),
    ('aaaaaaaa-0000-0000-0000-000000000001','forum-entreprises-2025','Forum entreprises',
     'Trente entreprises reçues par les élèves de deuxième année.', 'Hall de l''ENSIM',
     '2025-02-04 09:00+00','2025-02-04 17:00+00','PUBLIE', NULL, false, NULL, NULL)
;

-- ------------------------------------------------------- partenaires
--
-- Le cas qui justifie le rattachement au mandat : « Crédit Mutuel » soutenait
-- le BDE en 2024-2025 et pas en 2025-2026. Dans une modélisation par
-- association, il réapparaîtrait tout seul sur la page en cours, logo compris,
-- sans que personne n'ait rien resigné.
INSERT INTO partenaire (mandat_id, nom, niveau, url, ordre, visible) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','Le Mans Métropole','OR','https://www.lemansmetropole.fr',0,true),
    ('aaaaaaaa-0000-0000-0000-000000000002','MMArena','ARGENT','https://www.mmarena.com',0,true),
    ('aaaaaaaa-0000-0000-0000-000000000002','Librairie Thuard','SOUTIEN',NULL,0,true),
    ('aaaaaaaa-0000-0000-0000-000000000002','Partenaire en discussion','BRONZE',NULL,0,false),

    ('aaaaaaaa-0000-0000-0000-000000000001','Crédit Mutuel','OR',NULL,0,true),
    ('aaaaaaaa-0000-0000-0000-000000000001','Le Mans Métropole','ARGENT','https://www.lemansmetropole.fr',0,true)
;

-- Campagne d'adhésion « early bird » : ouverte par le bureau 2025-2026 mais
-- couvrant 2026-2027. C'est le cas que la v1 du modèle ne savait pas exprimer.
INSERT INTO campagne_adhesion (id, association_id, couvre_annee_code, ouverte_par_mandat_id, statut, ouvre_le) VALUES
    ('dddddddd-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111',
     '2026-2027','aaaaaaaa-0000-0000-0000-000000000002','OUVERTE','2026-07-01 09:00+00')
;

INSERT INTO tarif_adhesion (campagne_id, libelle, montant_cents, public_cible) VALUES
    ('dddddddd-0000-0000-0000-000000000001','Étudiant ENSIM',1500,'ETUDIANT'),
    ('dddddddd-0000-0000-0000-000000000001','Extérieur',2500,'EXTERIEUR')
;

COMMIT;
