-- Rôles PostgreSQL, créés dès le développement pour que le profil « delivery »
-- emprunte exactement le même chemin qu'en production.
--
-- Le principe : le composant qui passe à l'échelle (la lecture publique) est
-- aussi celui qui détient le moins de droits. Il ne peut rien écrire, et une
-- faille dans le rendu public ne permet ni de modifier une page, ni de
-- s'attribuer un poste au bureau.

-- Rôle applicatif : écrit, mais ne possède pas le schéma (pas de DDL).
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

-- Les droits sont appliqués aux tables futures : Flyway ne les a pas encore
-- créées au moment où ce script s'exécute.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ensimasso_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ensimasso_app;

-- Lecture seule, strictement.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO ensimasso_ro;
