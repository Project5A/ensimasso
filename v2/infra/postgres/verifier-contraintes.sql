-- Vérification des garanties portées par la BASE, pas par le code Java.
--
-- Chaque bloc marqué « doit etre REFUSE » DOIT produire une ERREUR : c'est le
-- résultat attendu. Un script qui passe sans erreur signifie qu'une contrainte
-- a disparu.
--
--   make verif-contraintes
--   ou : psql "$DB_URL" -f infra/postgres/verifier-contraintes.sql
--
-- À lancer sur une base fraîchement migrée et VIDE.

\set ON_ERROR_STOP off
\set QUIET on
\pset tuples_only on

INSERT INTO association (id, slug, nom, type_asso) VALUES
 ('11111111-1111-1111-1111-111111111111','bde','BDE','BUREAU');
INSERT INTO annee_universitaire (code, debut, fin) VALUES
 ('2024-2025','2024-09-01','2025-08-31'),('2025-2026','2025-09-01','2026-08-31'),
 ('2026-2027','2026-09-01','2027-08-31');

\echo '--- 1. mandat elu en AVRIL (passation de printemps) ---'
INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut) VALUES
 ('aaaa0000-0000-4000-8000-000000000001','11111111-1111-1111-1111-111111111111',
  '2024-2025','2024-04-11 18:00+00','2025-04-10 18:00+00','CLOS');
\echo 'OK: mandat avril->avril accepte'

\echo '--- 2. mandat suivant contigu (pas de trou, pas de chevauchement) ---'
INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','11111111-1111-1111-1111-111111111111',
  '2025-2026','2025-04-10 18:00+00',NULL,'EN_FONCTION');
\echo 'OK: mandat contigu accepte'

\echo '--- 3. CHEVAUCHEMENT doit etre REFUSE ---'
INSERT INTO mandat (id, association_id, annee_code, debut_le, fin_le, statut) VALUES
 ('aaaa0000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111',
  '2026-2027','2025-06-01 18:00+00','2026-06-01 18:00+00','EN_FONCTION');

\echo '--- 4. la rentree de septembre tombe dans un mandat elu en avril ---'
SELECT 'periode contient 2025-09-01 : ' || (periode @> '2025-09-01 12:00+00'::timestamptz)::text
  FROM mandat WHERE id='aaaa0000-0000-4000-8000-000000000002';

\echo '--- 5. DEUX presidents dans un bureau doit etre REFUSE ---'
INSERT INTO membre_bureau (mandat_id, personne_id, poste, ordre) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','d0000000-0000-4000-8000-000000000001','PRESIDENT',0);
\echo 'OK: premier president insere'
INSERT INTO membre_bureau (mandat_id, personne_id, poste, ordre) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','d0000000-0000-4000-8000-000000000002','PRESIDENT',1);

\echo '--- 6. immuabilite du contenu publie ---'
INSERT INTO page (id, mandat_id, slug, titre) VALUES
 ('eeee0000-0000-4000-8000-000000000001','aaaa0000-0000-4000-8000-000000000002','accueil','Accueil');
INSERT INTO page_version (id, page_id, numero, statut, cree_par) VALUES
 ('ffff0000-0000-4000-8000-000000000001','eeee0000-0000-4000-8000-000000000001',1,'BROUILLON',
  'd0000000-0000-4000-8000-000000000001');
INSERT INTO bloc (id, page_version_id, ordre, type, schema_version, payload) VALUES
 ('bbbb0000-0000-4000-8000-000000000001','ffff0000-0000-4000-8000-000000000001',0,'RICH_TEXT',1,'{"doc":{}}');
UPDATE bloc SET payload='{"doc":{"v":2}}' WHERE id='bbbb0000-0000-4000-8000-000000000001';
\echo 'OK: bloc modifiable tant que BROUILLON'
UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par='d0000000-0000-4000-8000-000000000001'
 WHERE id='ffff0000-0000-4000-8000-000000000001';
\echo 'OK: publication effectuee'

\echo '--- 7. modifier un bloc PUBLIE doit etre REFUSE ---'
UPDATE bloc SET payload='{"doc":{"v":3}}' WHERE id='bbbb0000-0000-4000-8000-000000000001';

\echo '--- 8. DEPUBLIER doit etre REFUSE ---'
UPDATE page_version SET statut='BROUILLON' WHERE id='ffff0000-0000-4000-8000-000000000001';

\echo '--- 9. DEUX versions publiees doit etre REFUSE ---'
INSERT INTO page_version (id, page_id, numero, statut, cree_par, publie_par, publie_le) VALUES
 ('ffff0000-0000-4000-8000-000000000002','eeee0000-0000-4000-8000-000000000001',2,'PUBLIEE',
  'd0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000001',now());

\echo '--- 10. schema_version inexistant doit etre REFUSE ---'
INSERT INTO page_version (id, page_id, numero, statut, cree_par) VALUES
 ('ffff0000-0000-4000-8000-000000000003','eeee0000-0000-4000-8000-000000000001',3,'BROUILLON',
  'd0000000-0000-4000-8000-000000000001');
INSERT INTO bloc (page_version_id, ordre, type, schema_version, payload) VALUES
 ('ffff0000-0000-4000-8000-000000000003',0,'RICH_TEXT',99,'{}');

\echo '--- 11. early bird : adhesion de juillet couvrant l annee suivante ---'
INSERT INTO campagne_adhesion (id, association_id, couvre_annee_code, ouverte_par_mandat_id, statut) VALUES
 ('dddd0000-0000-4000-8000-000000000001','11111111-1111-1111-1111-111111111111',
  '2026-2027','aaaa0000-0000-4000-8000-000000000002','OUVERTE');
INSERT INTO adhesion (personne_id, association_id, couvre_annee_code, vendue_par_mandat_id,
                      montant_paye_cents, statut, activee_le) VALUES
 ('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111',
  '2026-2027','aaaa0000-0000-4000-8000-000000000002',1500,'ACTIVE',now());
SELECT 'adherent le 2026-07-15 (achat early bird, annee pas encore courante) : '
  || est_adherent('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111','2026-07-15'::timestamptz)::text;
SELECT 'adherent le 2026-10-01 (annee couverte devenue courante) : '
  || est_adherent('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111','2026-10-01'::timestamptz)::text;

\echo '--- 12. double adhesion meme annee doit etre REFUSE ---'
INSERT INTO adhesion (personne_id, association_id, couvre_annee_code, vendue_par_mandat_id,
                      montant_paye_cents, statut) VALUES
 ('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111',
  '2026-2027','aaaa0000-0000-4000-8000-000000000002',1500,'ACTIVE');

\echo '--- 12b. remboursee, la place se libere : readherer doit etre ACCEPTE ---'
-- L'unicite portait sur tous les statuts : une adhesion REMBOURSEE fermait
-- l'annee a l'etudiant pour toujours. Elle est partielle depuis V12, et ce
-- bloc doit passer SANS erreur — c'est le seul du script dans ce cas parmi
-- les adhesions, et c'est voulu.
UPDATE adhesion SET statut='REMBOURSEE'
 WHERE personne_id='d0000000-0000-4000-8000-000000000003'
   AND couvre_annee_code='2026-2027';
INSERT INTO adhesion (personne_id, association_id, couvre_annee_code, vendue_par_mandat_id,
                      montant_paye_cents, statut) VALUES
 ('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111',
  '2026-2027','aaaa0000-0000-4000-8000-000000000002',1500,'ACTIVE');
SELECT 'adhesions 2026-2027 de cette personne (1 remboursee + 1 active = 2) : '
  || count(*)::text FROM adhesion
 WHERE personne_id='d0000000-0000-4000-8000-000000000003'
   AND couvre_annee_code='2026-2027';

\echo '--- 12c. ... mais une SECONDE vivante reste REFUSEE ---'
INSERT INTO adhesion (personne_id, association_id, couvre_annee_code, vendue_par_mandat_id,
                      montant_paye_cents, statut) VALUES
 ('d0000000-0000-4000-8000-000000000003','11111111-1111-1111-1111-111111111111',
  '2026-2027','aaaa0000-0000-4000-8000-000000000002',1500,'EN_ATTENTE_PAIEMENT');

\echo '--- 13. une URL ne peut pas etre stockee comme cle de media (STOR-01) ---'
INSERT INTO media_asset (association_id, annee_code, cle, content_type, statut, depose_par) VALUES
 ('11111111-1111-1111-1111-111111111111','2025-2026',
  'https://compte.blob.core.windows.net/userphotos/x.jpg?sv=2024&sig=abc',
  'image/jpeg','ATTENTE_DEPOT','d0000000-0000-4000-8000-000000000001');

\echo '--- 14. une cle avec chaine de requete est refusee ---'
INSERT INTO media_asset (association_id, annee_code, cle, content_type, statut, depose_par) VALUES
 ('11111111-1111-1111-1111-111111111111','2025-2026','bde/2025-2026/x.jpg?token=abc',
  'image/jpeg','ATTENTE_DEPOT','d0000000-0000-4000-8000-000000000001');

\echo '--- 15. une cle ordinaire est acceptee ---'
INSERT INTO media_asset (association_id, annee_code, cle, content_type, statut, depose_par, confirme_le) VALUES
 ('11111111-1111-1111-1111-111111111111','2025-2026','bde/2025-2026/9f1c.jpg',
  'image/jpeg','DISPONIBLE','d0000000-0000-4000-8000-000000000001', now());
\echo 'OK: cle d objet acceptee'

\echo '--- 16. DISPONIBLE sans date de confirmation doit etre REFUSE ---'
INSERT INTO media_asset (association_id, annee_code, cle, content_type, statut, depose_par) VALUES
 ('11111111-1111-1111-1111-111111111111','2025-2026','bde/2025-2026/incoherent.jpg',
  'image/jpeg','DISPONIBLE','d0000000-0000-4000-8000-000000000001');

\echo '--- 17. media_usage ne peut pas designer un media inexistant ---'
INSERT INTO media_usage (media_key, page_version_id) VALUES
 ('bde/2025-2026/nexiste-pas.jpg','ffff0000-0000-4000-8000-000000000001');

\echo '--- 18. un media reference par une version figee ne peut pas etre supprime ---'
INSERT INTO media_usage (media_key, page_version_id) VALUES
 ('bde/2025-2026/9f1c.jpg','ffff0000-0000-4000-8000-000000000001');
\echo 'OK: usage enregistre'
DELETE FROM media_asset WHERE cle = 'bde/2025-2026/9f1c.jpg';

\echo '--- 19. le total d une commande DOIT egaler la somme de ses lignes ---'
BEGIN;
INSERT INTO commande (id, personne_id, association_id, statut, montant_total_cents) VALUES
 ('c0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000003',
  '11111111-1111-1111-1111-111111111111','OUVERTE', 1500);
INSERT INTO ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents) VALUES
 ('c0000000-0000-4000-8000-000000000001','ADHESION','a0000000-0000-4000-8000-000000000001',
  'Adhesion', 900);
COMMIT;

\echo '--- 20. total coherent : accepte ---'
BEGIN;
INSERT INTO commande (id, personne_id, association_id, statut, montant_total_cents) VALUES
 ('c0000000-0000-4000-8000-000000000002','d0000000-0000-4000-8000-000000000003',
  '11111111-1111-1111-1111-111111111111','OUVERTE', 1500);
INSERT INTO ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents) VALUES
 ('c0000000-0000-4000-8000-000000000002','ADHESION','a0000000-0000-4000-8000-000000000002',
  'Adhesion', 1500);
COMMIT;
\echo 'OK: commande coherente acceptee'

\echo '--- 21. un meme droit ne peut pas etre facture deux fois ---'
BEGIN;
INSERT INTO commande (id, personne_id, association_id, statut, montant_total_cents) VALUES
 ('c0000000-0000-4000-8000-000000000003','d0000000-0000-4000-8000-000000000003',
  '11111111-1111-1111-1111-111111111111','OUVERTE', 1500);
INSERT INTO ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents) VALUES
 ('c0000000-0000-4000-8000-000000000003','ADHESION','a0000000-0000-4000-8000-000000000002',
  'Doublon', 1500);
COMMIT;

\echo '--- 22. les lignes d une commande PAYEE sont figees ---'
UPDATE commande SET statut='PAYEE', payee_le=now() WHERE id='c0000000-0000-4000-8000-000000000002';
INSERT INTO ligne_commande (commande_id, type_ligne, reference_id, libelle, montant_cents) VALUES
 ('c0000000-0000-4000-8000-000000000002','BILLET','b0000000-0000-4000-8000-000000000001',
  'Ajout apres paiement', 500);

\echo '--- 23. un meme paiement ne peut pas etre enregistre deux fois ---'
INSERT INTO paiement (commande_id, reference, montant_cents, statut) VALUES
 ('c0000000-0000-4000-8000-000000000002','pi_3ABC',1500,'REUSSI');
\echo 'OK: paiement enregistre'
INSERT INTO paiement (commande_id, reference, montant_cents, statut) VALUES
 ('c0000000-0000-4000-8000-000000000002','pi_3ABC',1500,'REUSSI');

\echo '--- 24. un meme evenement webhook ne peut pas etre traite deux fois ---'
INSERT INTO evenement_stripe (id, type) VALUES ('evt_1ABC','payment_intent.succeeded');
\echo 'OK: evenement enregistre'
INSERT INTO evenement_stripe (id, type) VALUES ('evt_1ABC','payment_intent.succeeded');

\echo '--- 25. le journal comptable est append-only ---'
INSERT INTO ecriture_ledger (commande_id, association_id, sens, montant_cents, motif) VALUES
 ('c0000000-0000-4000-8000-000000000002','11111111-1111-1111-1111-111111111111',
  'ENTREE',1500,'Encaissement');
\echo 'OK: ecriture enregistree'
UPDATE ecriture_ledger SET montant_cents = 1 WHERE motif = 'Encaissement';

\echo '--- 26. ... et indestructible ---'
DELETE FROM ecriture_ledger WHERE motif = 'Encaissement';

\echo '--- 27. un evenement ANNULE sans date d annulation doit etre REFUSE ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le, statut) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','gala','Gala','2026-10-03 20:00+00','ANNULE');

\echo '--- 28. ... avec sa date, il passe, et il RESTE visible ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le, statut, annule_le, motif_annulation) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','gala','Gala','2026-10-03 20:00+00','ANNULE',
  now(),'Salle indisponible');
\echo 'OK: evenement annule enregistre, avec son motif'

\echo '--- 29. deux evenements de meme slug dans un meme mandat : REFUSE ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','gala','Gala bis','2026-11-03 20:00+00');

\echo '--- 30. ... mais le mandat precedent garde le sien ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le) VALUES
 ('aaaa0000-0000-4000-8000-000000000001','gala','Gala 2024','2024-11-23 20:00+00');
\echo 'OK: chaque mandat a son propre agenda'

\echo '--- 31. une fin anterieure au debut doit etre REFUSEE ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le, fin_le) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','retro','Retro',
  '2026-10-03 20:00+00','2026-10-03 18:00+00');

\echo '--- 32. un lien « javascript: » doit etre REFUSE ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le, lien) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','xss','XSS','2026-10-03 20:00+00',
  'javascript:alert(1)');

\echo '--- 33. une URL a la place d une cle de media doit etre REFUSEE ---'
INSERT INTO evenement (mandat_id, slug, titre, debut_le, media_key) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','url','Url','2026-10-03 20:00+00',
  'https://exemple.org/affiche.png?sig=abc');

\echo '--- 34. le MEME partenaire peut exister sur deux mandats ---'
INSERT INTO partenaire (mandat_id, nom, niveau) VALUES
 ('aaaa0000-0000-4000-8000-000000000001','Le Mans Metropole','OR'),
 ('aaaa0000-0000-4000-8000-000000000002','Le Mans Metropole','ARGENT');
\echo 'OK: un partenariat se renegocie chaque annee, niveau compris'

\echo '--- 35. ... mais pas deux fois sur le meme mandat ---'
INSERT INTO partenaire (mandat_id, nom, niveau) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','Le Mans Metropole','OR');

\echo '--- 36. un niveau de partenariat invente doit etre REFUSE ---'
INSERT INTO partenaire (mandat_id, nom, niveau) VALUES
 ('aaaa0000-0000-4000-8000-000000000002','Platine SA','PLATINE');

\echo '--- 37. supprimer un mandat emporte son agenda et ses partenaires ---'
DELETE FROM mandat WHERE id='aaaa0000-0000-4000-8000-000000000001';
SELECT 'evenements orphelins : ' || count(*)::text
  FROM evenement WHERE mandat_id='aaaa0000-0000-4000-8000-000000000001';
SELECT 'partenaires orphelins : ' || count(*)::text
  FROM partenaire WHERE mandat_id='aaaa0000-0000-4000-8000-000000000001';
\echo '(les deux doivent valoir 0 : rien ne survit a son mandat)'
