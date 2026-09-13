-- Deux années universitaires pouvaient se chevaucher.
--
-- La table n'avait qu'une clé primaire sur le code et « fin > debut ». Rien
-- n'empêchait d'inscrire 2030-2031 du 1er septembre au 31 août et 2031-2032 à
-- partir du 1er juin — vérifié, les deux lignes s'insèrent sans broncher.
--
-- Or tout le code suppose « au plus une année couvre un jour donné » :
--
--     @Query("select a from AnneeUniversitaire a where :jour between a.debut and a.fin")
--     Optional<AnneeUniversitaire> anneeCouvrant(LocalDate jour);
--
-- Un Optional. Deux lignes qui correspondent, et Spring Data lève
-- IncorrectResultSizeDataAccessException : chaque vérification d'adhésion
-- devient un 500, pour toute l'école, jusqu'à ce que quelqu'un corrige la
-- table à la main. L'hypothèse était dans le code, jamais dans la base.
--
-- Le mandat, lui, porte déjà cette garantie par une contrainte d'exclusion
-- GiST. On applique ici la même, avec les mêmes bornes que la requête :
-- `between` est inclusif des deux côtés, donc l'intervalle l'est aussi. Deux
-- années consécutives qui se touchent sans se recouvrir — 31 août puis
-- 1er septembre — restent acceptées.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE annee_universitaire
    ADD CONSTRAINT annee_sans_chevauchement
        EXCLUDE USING gist (daterange(debut, fin, '[]') WITH &&);

-- La contrainte doit MORDRE. Une migration qui ajoute une règle sans vérifier
-- qu'elle refuse quoi que ce soit est une migration qui rassure à tort.
DO $$
BEGIN
    BEGIN
        INSERT INTO annee_universitaire (code, debut, fin)
        VALUES ('9998-9999', '9998-09-01', '9999-08-31'),
               ('9999-0000', '9999-06-01', '9999-12-31');
        RAISE EXCEPTION 'la contrainte de chevauchement n''a rien refusé';
    EXCEPTION
        WHEN exclusion_violation THEN
            NULL;   -- attendu : c'est la preuve que la contrainte mord
    END;
END $$;

-- Et que deux années consécutives QUI SE TOUCHENT restent possibles, sans quoi
-- on aurait remplacé un défaut par un autre.
DO $$
BEGIN
    INSERT INTO annee_universitaire (code, debut, fin)
    VALUES ('9990-9991', '9990-09-01', '9991-08-31'),
           ('9991-9992', '9991-09-01', '9992-08-31');
    DELETE FROM annee_universitaire WHERE code IN ('9990-9991', '9991-9992');
END $$;
