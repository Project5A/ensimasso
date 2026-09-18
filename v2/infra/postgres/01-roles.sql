-- Rôles PostgreSQL, créés dès le développement pour que le profil « delivery »
-- emprunte exactement le même chemin qu'en production.
--
-- Le principe : le composant qui passe à l'échelle (la lecture publique) est
-- aussi celui qui détient le moins de droits. Il ne peut rien écrire, et une
-- faille dans le rendu public ne permet ni de modifier une page, ni de
-- s'attribuer un poste au bureau.

-- Rôle applicatif : il écrit les données ET migre le schéma.
--
-- Ce commentaire disait « ne possède pas le schéma (pas de DDL) ». C'est
-- l'inverse de ce que fait le déploiement : `core` exécute Flyway au démarrage
-- — c'est même la raison pour laquelle il tourne en réplique unique avec une
-- stratégie Recreate, « deux versions du schéma en vol pendant une bascule
-- étant la façon la plus sûre de perdre des données un mardi soir ». Sans
-- CREATE sur le schéma, ce démarrage échoue : l'application n'aurait jamais pu
-- migrer une base neuve en production.
--
-- La séparation qui compte est ailleurs, et elle tient : `delivery`, le
-- composant exposé qui passe à l'échelle, est en LECTURE SEULE.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ensimasso_app') THEN
        CREATE ROLE ensimasso_app LOGIN PASSWORD 'dev_seulement';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ensimasso_ro') THEN
        CREATE ROLE ensimasso_ro LOGIN PASSWORD 'dev_seulement';
    END IF;
END $$;

GRANT CONNECT ON DATABASE ensimasso TO ensimasso_app, ensimasso_ro;
GRANT USAGE ON SCHEMA public TO ensimasso_app, ensimasso_ro;

-- Les extensions sont posées ICI, par l'administrateur, et non par Flyway.
--
-- V1 les demande en « CREATE EXTENSION IF NOT EXISTS », ce qui ne coûte rien
-- quand elles sont déjà là — mais les CRÉER exige le droit CREATE sur la BASE,
-- que ensimasso_app n'a pas et ne doit pas avoir. Sur une base neuve en
-- production, la toute première migration s'arrêtait donc sur « permission
-- denied to create extension "btree_gist" », avant même la première table.
-- Vérifié en rejouant la séquence réelle contre un PostgreSQL 16 : échec sur
-- V1 sans ces deux lignes, migration complète avec.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- C'est ensimasso_app qui crée les tables, puisque c'est lui qui migre.
-- Depuis PostgreSQL 15, le rôle public n'a plus CREATE sur le schéma public :
-- sans cette ligne, Flyway s'arrête sur « permission denied for schema public »
-- au tout premier démarrage.
GRANT CREATE ON SCHEMA public TO ensimasso_app;

-- Les droits sont appliqués aux tables futures : Flyway ne les a pas encore
-- créées au moment où ce script s'exécute.
--
-- « FOR ROLE ensimasso_app » n'est pas une précision de style. Sans lui,
-- ALTER DEFAULT PRIVILEGES ne vaut que pour les objets créés par le rôle QUI
-- EXÉCUTE CE SCRIPT — l'administrateur. Les tables, elles, sont créées par
-- ensimasso_app au démarrage de l'application : elles ne recevaient donc aucun
-- de ces droits, et ensimasso_ro ne pouvait RIEN lire. En développement le
-- défaut ne se voyait pas, parce que le script et Flyway y tournent sous le
-- même rôle — la configuration qui ne ressemble pas à la production est
-- exactement celle qui cache ce genre de chose.
ALTER DEFAULT PRIVILEGES FOR ROLE ensimasso_app IN SCHEMA public
    GRANT SELECT ON TABLES TO ensimasso_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE ensimasso_app IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ensimasso_ro;

-- Et pour les tables déjà présentes — base migrée avant l'exécution de ce
-- script, ou script rejoué après une migration.
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ensimasso_ro;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ensimasso_ro;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ensimasso_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ensimasso_app;
