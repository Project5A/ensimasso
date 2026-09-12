-- Jeu de données de développement : trois associations réelles de l'ENSIM,
-- réparties sur deux années, avec une passation de printemps déjà jouée.
--
-- L'objectif est que la base locale ressemble à la réalité : sans cela on
-- développe contre un cas vide et on découvre le cas « archive » en production.

BEGIN;

INSERT INTO annee_universitaire (code, debut, fin) VALUES
    ('2024-2025','2024-09-01','2025-08-31'),
    ('2025-2026','2025-09-01','2026-08-31'),
    ('2026-2027','2026-09-01','2027-08-31')
ON CONFLICT (code) DO NOTHING;

INSERT INTO association (id, slug, nom, type_asso, fondee_le) VALUES
    ('11111111-1111-1111-1111-111111111111','bde','Bureau des Élèves','BUREAU','1988-10-01'),
    ('22222222-2222-2222-2222-222222222222','bds','Bureau des Sports','BUREAU','1992-09-15'),
    ('33333333-3333-3333-3333-333333333333','kfet','K-Fêt','CLUB','1995-03-01')
ON CONFLICT (slug) DO NOTHING;

-- BDE : mandat 2024-2025 CLOS (élu en avril 2024), puis 2025-2026 EN_FONCTION.
-- C'est exactement le scénario de passation de printemps que le modèle par
-- année était incapable de représenter.
INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut, investi_le, clos_le) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111',
     '2024-2025','2024-04-11 18:00+00','2025-04-10 18:00+00','CLOS',
     '2024-04-11 18:00+00','2025-04-10 18:00+00')
ON CONFLICT DO NOTHING;

INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut, investi_le) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111',
     '2025-2026','2025-04-10 18:00+00', NULL,'EN_FONCTION','2025-04-10 18:00+00'),
    ('bbbbbbbb-0000-0000-0000-000000000001','22222222-2222-2222-2222-222222222222',
     '2025-2026','2025-09-01 12:00+00', NULL,'EN_FONCTION','2025-09-01 12:00+00'),
    ('cccccccc-0000-0000-0000-000000000001','33333333-3333-3333-3333-333333333333',
     '2025-2026','2025-09-15 12:00+00', NULL,'EN_FONCTION','2025-09-15 12:00+00')
ON CONFLICT DO NOTHING;

-- Bureaux. Les identifiants de personne correspondent aux « sub » des
-- utilisateurs importés dans le royaume Keycloak de développement.
INSERT INTO membre_bureau (mandat_id, personne_id, poste, ordre, titre_affiche) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000001','PRESIDENT',0,'Présidente'),
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000002','TRESORIER',1,NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002','d0000000-0000-4000-8000-000000000003','RESP_COM',2,'Resp. Communication'),
    ('aaaaaaaa-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000009','PRESIDENT',0,NULL),
    ('bbbbbbbb-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000004','PRESIDENT',0,NULL),
    ('cccccccc-0000-0000-0000-000000000001','d0000000-0000-4000-8000-000000000005','PRESIDENT',0,NULL)
ON CONFLICT DO NOTHING;

-- Une page d'accueil publiée pour le BDE, sur le mandat en cours.
INSERT INTO page (id, mandat_id, slug, titre, ordre_menu) VALUES
    ('eeeeeeee-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002','accueil','Accueil',0)
ON CONFLICT DO NOTHING;

INSERT INTO page_version (id, page_id, numero, statut, cree_par, publie_par, publie_le) VALUES
    ('ffffffff-0000-0000-0000-000000000001','eeeeeeee-0000-0000-0000-000000000001',1,'PUBLIEE',
     'd0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000001', now())
ON CONFLICT DO NOTHING;

INSERT INTO bloc (page_version_id, ordre, type, schema_version, payload) VALUES
    ('ffffffff-0000-0000-0000-000000000001',0,'HERO',1,
     '{"titre":"Bureau des Élèves","sousTitre":"La vie étudiante à l''ENSIM","hauteur":"MOYENNE","overlay":"DEGRADE"}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',1,'STATS',1,
     '{"items":[{"libelle":"Adhérents","valeur":"420"},{"libelle":"Évènements","valeur":"35"},{"libelle":"Clubs","valeur":"12"}]}'::jsonb),
    ('ffffffff-0000-0000-0000-000000000001',2,'TEAM_GRID',1,
     '{"source":"MANDAT_DE_LA_PAGE","colonnes":3}'::jsonb)
ON CONFLICT DO NOTHING;

-- Campagne d'adhésion « early bird » : ouverte par le bureau 2025-2026 mais
-- couvrant 2026-2027. C'est le cas que la v1 du modèle ne savait pas exprimer.
INSERT INTO campagne_adhesion (id, association_id, couvre_annee_code, ouverte_par_mandat_id, statut, ouvre_le) VALUES
    ('dddddddd-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111',
     '2026-2027','aaaaaaaa-0000-0000-0000-000000000002','OUVERTE','2026-07-01 09:00+00')
ON CONFLICT DO NOTHING;

INSERT INTO tarif_adhesion (campagne_id, libelle, montant_cents, public_cible) VALUES
    ('dddddddd-0000-0000-0000-000000000001','Étudiant ENSIM',1500,'ETUDIANT'),
    ('dddddddd-0000-0000-0000-000000000001','Extérieur',2500,'EXTERIEUR')
ON CONFLICT DO NOTHING;

COMMIT;
