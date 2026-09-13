-- Annuler une passation était impossible : la fonction échouait à tous les coups.
--
-- `annuler()` passe la passation en ANNULEE, puis supprime le mandat entrant
-- pour libérer l'année. Mais la ligne passation continue de référencer ce
-- mandat, et la clé étrangère était en NO ACTION :
--
--   ERROR: update or delete on table "mandat" violates foreign key constraint
--          "passation_mandat_entrant_id_fkey" on table "passation"
--
-- Ce n'était pas intermittent. L'index unique passation_par_mandat_entrant
-- garantit qu'il existe toujours exactement une ligne référente, et annuler()
-- n'est atteignable que tant que le mandat entrant est en PREPARATION —
-- c'est-à-dire dans tous les cas. Le président qui s'était trompé de dates
-- recevait un 500, la transaction était annulée, et il restait coincé :
-- `preparer()` relancé refusait (« un mandat existe déjà pour 2026-2027 »,
-- doublé de l'index mandat_un_seul_par_annee), et l'index partiel
-- passation_une_seule_ouverte interdisait une seconde passation. Sortir de là
-- demandait un accès psql à la production.
--
-- Deux choix étaient possibles : supprimer aussi la ligne passation, ou la
-- garder en la détachant. On garde. Une passation avortée est un fait de
-- gouvernance — qui l'avait préparée, quand, combien de pages avaient été
-- clonées — et l'effacer pour contourner une contrainte technique reviendrait
-- à réécrire l'histoire de l'association. Le statut ANNULEE existait déjà dans
-- le CHECK : il devient enfin atteignable.
--
-- Le mandat entrant, lui, DOIT disparaître : mandat_un_seul_par_annee est un
-- index unique sur (association_id, annee_code), et le garder interdirait pour
-- toujours de repréparer la passation de cette année-là.

ALTER TABLE passation
    ALTER COLUMN mandat_entrant_id DROP NOT NULL;

ALTER TABLE passation
    DROP CONSTRAINT passation_mandat_entrant_id_fkey;

ALTER TABLE passation
    ADD CONSTRAINT passation_mandat_entrant_id_fkey
        FOREIGN KEY (mandat_entrant_id) REFERENCES mandat(id) ON DELETE SET NULL;

-- Une passation vivante pointe forcément vers son mandat entrant ; seule une
-- passation annulée peut l'avoir perdu. Sans cette règle, « détachable »
-- deviendrait « détaché n'importe quand ».
ALTER TABLE passation
    ADD CONSTRAINT passation_entrant_present_sauf_annulee
        CHECK (mandat_entrant_id IS NOT NULL OR statut = 'ANNULEE');
