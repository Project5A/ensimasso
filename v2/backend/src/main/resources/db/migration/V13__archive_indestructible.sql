-- L'archive se réécrivait : il suffisait de la SUPPRIMER par le haut.
--
-- V2 pose la « GARANTIE 2 — le contenu publié est immuable » et la confie au
-- trigger bloc_fige, qui refuse tout INSERT, UPDATE ou DELETE sur un bloc dont
-- la version est PUBLIEE ou ARCHIVEE. Vérifié : il refuse bien.
--
-- Mais bloc → page_version → page → mandat est une chaîne d'ON DELETE CASCADE.
-- Rejoué contre un PostgreSQL 16 migré, sur une version PUBLIEE portant un
-- bloc :
--   DELETE FROM bloc WHERE page_version_id = … ;
--     ERROR: bloc figé : la version … est PUBLIEE
--   DELETE FROM page_version WHERE id = … ;
--     DELETE 1        ← et le bloc part avec elle, sans un mot
-- La garantie tenait contre la modification et pas contre l'effacement. Or
-- effacer une archive et la réécrire, c'est la même chose du point de vue de
-- quelqu'un qui cherche la page de son bureau trois ans plus tard.
--
-- La raison technique tient en une ligne : dans une suppression en cascade, la
-- ligne PARENT est déjà supprimée quand le trigger de l'enfant s'exécute. Le
-- SELECT de bloc_fige ne trouve alors plus la version, v_statut vaut NULL,
-- « NULL IN (...) » n'est pas vrai, et le bloc s'en va.
--
-- On ne rattrape donc pas cela au niveau du bloc : on ferme chaque porte à son
-- propre étage. Chaque garde vérifie ce qu'elle sait, et traite « mon parent
-- n'existe déjà plus » comme « la cascade vient de mon parent, qui a été
-- vérifié par SA garde ». C'est vrai parce qu'il n'y a aucun autre moyen que la
-- ligne parente disparaisse au milieu d'une instruction.
--
-- L'exception, et la seule : un mandat en PREPARATION. Il n'a jamais gouverné,
-- le portail public ne rend que mandatEnFonction, et les archives excluent
-- explicitement PREPARATION. Annuler une passation supprime ce mandat — c'est
-- ce que fait ServicePassation.annuler() — et rien de public n'est alors perdu.

-- ------------------------------------------------------------------ mandat
CREATE FUNCTION gouvernance_refuse_suppression_mandat() RETURNS trigger AS $$
BEGIN
    IF OLD.statut <> 'PREPARATION' THEN
        RAISE EXCEPTION
            'mandat % : un mandat % ne se supprime pas, il porte une archive',
            OLD.id, OLD.statut
            USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER mandat_non_supprimable
    BEFORE DELETE ON mandat
    FOR EACH ROW EXECUTE FUNCTION gouvernance_refuse_suppression_mandat();

-- -------------------------------------------------------------------- page
CREATE FUNCTION contenu_refuse_suppression_page() RETURNS trigger AS $$
DECLARE
    v_statut_mandat text;
BEGIN
    SELECT m.statut INTO v_statut_mandat FROM mandat m WHERE m.id = OLD.mandat_id;

    -- Mandat absent : la cascade vient de sa propre suppression, déjà vérifiée.
    IF v_statut_mandat IS NULL OR v_statut_mandat = 'PREPARATION' THEN
        RETURN OLD;
    END IF;
    IF EXISTS (SELECT 1 FROM page_version v
                WHERE v.page_id = OLD.id AND v.statut IN ('PUBLIEE','ARCHIVEE')) THEN
        RAISE EXCEPTION
            'page % : elle porte une version publiée ou archivée, qui ne s''efface pas',
            OLD.id
            USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER page_non_supprimable_si_publiee
    BEFORE DELETE ON page
    FOR EACH ROW EXECUTE FUNCTION contenu_refuse_suppression_page();

-- ------------------------------------------------------------ page_version
CREATE FUNCTION contenu_refuse_suppression_version() RETURNS trigger AS $$
DECLARE
    v_statut_mandat text;
BEGIN
    IF OLD.statut NOT IN ('PUBLIEE','ARCHIVEE') THEN
        RETURN OLD;                 -- un brouillon s'abandonne librement
    END IF;

    SELECT m.statut INTO v_statut_mandat
      FROM page p JOIN mandat m ON m.id = p.mandat_id
     WHERE p.id = OLD.page_id;

    -- Page ou mandat absents : cascade depuis un parent déjà vérifié.
    IF v_statut_mandat IS NULL OR v_statut_mandat = 'PREPARATION' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION
        'version % : une version % ne s''efface pas, c''est l''archive',
        OLD.id, OLD.statut
        USING ERRCODE = '23514';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER page_version_non_supprimable_si_publiee
    BEFORE DELETE ON page_version
    FOR EACH ROW EXECUTE FUNCTION contenu_refuse_suppression_version();

-- ------------------------------------------------------------------ thème
--
-- theme_version n'avait AUCUNE des garanties de page_version : ni gel après
-- publication, ni transitions contrôlées. Vérifié sur une base migrée :
--   UPDATE theme_version SET tokens = … WHERE statut = 'PUBLIEE';  → UPDATE 1
--   UPDATE theme_version SET statut = 'BROUILLON' WHERE …;         → UPDATE 1
-- La première réécrit les couleurs d'une archive ; la seconde dépublie, ce qui
-- libère l'index theme_une_seule_publiee et rend le thème à nouveau modifiable.
--
-- L'entité Java porte pourtant la règle — « un thème PUBLIEE ne se modifie
-- plus : ouvrez un brouillon ». C'est exactement la situation que V2 décrivait
-- pour les pages avant son trigger : une garantie qui reposait sur une
-- convention, « le code ne fait pas d'UPDATE ». Le thème n'a jamais reçu le
-- sien. Il le reçoit ici, avec les mêmes bornes.
--
-- IS DISTINCT FROM, et non « tout UPDATE » : Hibernate réécrit TOUTES les
-- colonnes à chaque sauvegarde, y compris celles qui n'ont pas changé. Archiver
-- un thème renvoie donc ses tokens à l'identique, et une garde plus grossière
-- casserait la publication elle-même.
CREATE FUNCTION contenu_theme_fige() RETURNS trigger AS $$
BEGIN
    IF OLD.statut IN ('PUBLIEE','ARCHIVEE')
       AND NEW.tokens IS DISTINCT FROM OLD.tokens THEN
        RAISE EXCEPTION
            'thème figé : la version % est % et ses jetons ne changent plus',
            OLD.id, OLD.statut
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER theme_fige
    BEFORE UPDATE ON theme_version
    FOR EACH ROW EXECUTE FUNCTION contenu_theme_fige();

CREATE TRIGGER theme_transition
    BEFORE UPDATE OF statut ON theme_version
    FOR EACH ROW EXECUTE FUNCTION contenu_transition_version();

CREATE FUNCTION contenu_refuse_suppression_theme() RETURNS trigger AS $$
DECLARE
    v_statut_mandat text;
BEGIN
    IF OLD.statut NOT IN ('PUBLIEE','ARCHIVEE') THEN
        RETURN OLD;
    END IF;
    SELECT m.statut INTO v_statut_mandat FROM mandat m WHERE m.id = OLD.mandat_id;
    IF v_statut_mandat IS NULL OR v_statut_mandat = 'PREPARATION' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION
        'thème % : une version % ne s''efface pas, c''est l''identité archivée',
        OLD.id, OLD.statut
        USING ERRCODE = '23514';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER theme_version_non_supprimable_si_publiee
    BEFORE DELETE ON theme_version
    FOR EACH ROW EXECUTE FUNCTION contenu_refuse_suppression_theme();

-- -------------------------------------------------------------------------
-- Les gardes doivent MORDRE, et laisser passer ce qui doit passer.
-- Huit cas, joués pour de vrai, à l'endroit même où les règles sont posées.
-- -------------------------------------------------------------------------
DO $$
DECLARE
    asso    uuid := '00000000-0000-0000-0000-0000000000d1';
    gouv    uuid := '00000000-0000-0000-0000-0000000000d2';   -- EN_FONCTION
    prep    uuid := '00000000-0000-0000-0000-0000000000d3';   -- PREPARATION
    pg      uuid := '00000000-0000-0000-0000-0000000000d4';
    ver     uuid := '00000000-0000-0000-0000-0000000000d5';
    thm     uuid := '00000000-0000-0000-0000-0000000000d6';
    pg2     uuid := '00000000-0000-0000-0000-0000000000d7';
    ver2    uuid := '00000000-0000-0000-0000-0000000000d8';
    restant int;
BEGIN
    -- Tout ce qui suit est joué DANS un bloc à exception, donc sur un point de
    -- reprise implicite : la sentinelle levée à la fin ramène la base à son
    -- état d'avant, jeu d'essai compris. C'est la seule façon de nettoyer
    -- derrière soi ici — les gardes qu'on vient de poser refusent, à juste
    -- titre, de supprimer la page publiée qu'on a créée pour les éprouver.
    BEGIN
    INSERT INTO association (id, slug, nom, type_asso)
    VALUES (asso, 'verif-v13', 'Vérification V13', 'BUREAU');
    INSERT INTO annee_universitaire (code, debut, fin)
    VALUES ('9960-9961', '9960-09-01', '9961-08-31'),
           ('9961-9962', '9961-09-01', '9962-08-31');
    INSERT INTO mandat (id, association_id, annee_code, debut_le, statut)
    VALUES (gouv, asso, '9960-9961', '9960-09-01T00:00:00Z', 'EN_FONCTION');
    INSERT INTO mandat (id, association_id, annee_code, debut_le, statut)
    VALUES (prep, asso, '9961-9962', '9961-09-01T00:00:00Z', 'PREPARATION');

    -- Une page publiée du bureau EN FONCTION, construite comme le fait le code :
    -- brouillon, bloc, puis publication.
    INSERT INTO page (id, mandat_id, slug, titre, ordre_menu)
    VALUES (pg, gouv, 'accueil', 'Accueil', 0);
    INSERT INTO page_version (id, page_id, numero, statut, cree_par)
    VALUES (ver, pg, 1, 'BROUILLON', gen_random_uuid());
    INSERT INTO bloc (id, page_version_id, ordre, type, schema_version, payload)
    VALUES (gen_random_uuid(), ver, 0, 'RICH_TEXT', 1, '{"doc":{}}');
    UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par=gen_random_uuid()
     WHERE id = ver;

    -- 1. La version publiée ne s'efface pas.
    BEGIN
        DELETE FROM page_version WHERE id = ver;
        RAISE EXCEPTION 'une version PUBLIEE a été supprimée : la garde ne mord pas';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- 2. Ni par le dessus, en supprimant la page.
    BEGIN
        DELETE FROM page WHERE id = pg;
        RAISE EXCEPTION 'une page portant une version publiée a été supprimée';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- 3. Ni par le dessus du dessus, en supprimant le mandat.
    BEGIN
        DELETE FROM mandat WHERE id = gouv;
        RAISE EXCEPTION 'un mandat EN_FONCTION a été supprimé';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- 4. Un brouillon, lui, s'abandonne : on n'a pas remplacé un défaut par une
    --    paralysie.
    INSERT INTO page_version (id, page_id, numero, statut, cree_par)
    VALUES (ver2, pg, 2, 'BROUILLON', gen_random_uuid());
    DELETE FROM page_version WHERE id = ver2;

    -- 5. Et le mandat en PREPARATION s'annule, avec tout ce qu'il a préparé —
    --    c'est ServicePassation.annuler(), et rien de public n'y est perdu.
    INSERT INTO page (id, mandat_id, slug, titre, ordre_menu)
    VALUES (pg2, prep, 'accueil', 'Accueil', 0);
    INSERT INTO page_version (id, page_id, numero, statut, cree_par)
    VALUES ('00000000-0000-0000-0000-0000000000d9', pg2, 1, 'BROUILLON', gen_random_uuid());
    INSERT INTO bloc (id, page_version_id, ordre, type, schema_version, payload)
    VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000d9', 0,
            'RICH_TEXT', 1, '{"doc":{}}');
    UPDATE page_version SET statut='PUBLIEE', publie_le=now(), publie_par=gen_random_uuid()
     WHERE id = '00000000-0000-0000-0000-0000000000d9';
    DELETE FROM mandat WHERE id = prep;
    SELECT count(*) INTO restant FROM page WHERE mandat_id = prep;
    IF restant <> 0 THEN
        RAISE EXCEPTION 'la cascade du mandat en préparation n''a pas eu lieu';
    END IF;

    -- 6. Un thème publié ne se réécrit plus.
    INSERT INTO theme_version (id, mandat_id, numero, statut, tokens)
    VALUES (thm, gouv, 1, 'BROUILLON', '{"couleurPrimaire":"#123456"}');
    UPDATE theme_version SET statut='PUBLIEE' WHERE id = thm;
    BEGIN
        UPDATE theme_version SET tokens='{"couleurPrimaire":"#ff0000"}' WHERE id = thm;
        RAISE EXCEPTION 'les jetons d''un thème publié ont été réécrits';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- 7. Il ne se dépublie pas non plus — sans quoi il redeviendrait modifiable.
    BEGIN
        UPDATE theme_version SET statut='BROUILLON' WHERE id = thm;
        RAISE EXCEPTION 'un thème publié a été dépublié';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- 8. Mais l'archiver reste possible, jetons inchangés : c'est la séquence
    --    exacte de ServiceTheme.publier(), et Hibernate réécrit toutes les
    --    colonnes à chaque sauvegarde.
    UPDATE theme_version
       SET statut='ARCHIVEE', tokens='{"couleurPrimaire":"#123456"}'
     WHERE id = thm;

        RAISE EXCEPTION 'verification-v13-terminee';
    EXCEPTION
        WHEN raise_exception THEN
            -- Seule la sentinelle est avalée. Un échec de garde porte un autre
            -- message et repart : la migration doit alors s'arrêter.
            IF SQLERRM <> 'verification-v13-terminee' THEN
                RAISE;
            END IF;
    END;
END $$;
