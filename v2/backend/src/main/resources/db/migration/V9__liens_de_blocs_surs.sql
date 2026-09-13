-- Les champs href des blocs n'acceptaient aucune contrainte de schéma.
--
-- Le registre V4 a délibérément supprimé l'injection de HTML : RICH_TEXT
-- stocke un document structuré, jamais une chaîne de balisage, et EMBED
-- n'accepte qu'une liste blanche de fournisseurs plutôt qu'une iframe
-- arbitraire. La discipline était là. Elle n'a simplement jamais été portée
-- jusqu'aux champs d'URL : `href` était déclaré « chaîne de 512 caractères au
-- plus », sans motif.
--
-- `javascript:alert(document.cookie)` traversait donc la validation, et React
-- écrit un href tel quel — vérifié en rendant le bloc : l'URL arrivait intacte
-- dans le DOM d'une page PUBLIQUE. Il suffisait d'un responsable communication
-- pour exécuter du code chez chaque visiteur de l'association.
--
-- Les colonnes agenda.lien et partenaire.url, elles, portaient déjà leur CHECK
-- depuis V7 : c'est bien le payload des blocs qui manquait.
--
-- Le motif autorise http(s), mailto, et les chemins internes commençant par
-- « / ». Tout le reste est refusé — y compris data: et vbscript:.
--
-- Mise à jour SUR PLACE plutôt que nouvelle version de schéma : les blocs
-- existants sont en schema_version 1, et les faire basculer les rendrait
-- orphelins. Le cache de schémas compilés de ValidationBloc n'étant pas
-- invalidé à chaud, ce resserrement ne prend effet qu'au redémarrage — ce qui
-- est précisément le moment où Flyway s'exécute.

UPDATE type_bloc
SET json_schema = jsonb_set(
        json_schema,
        '{properties,actions,items,properties,href}',
        '{"type":"string","maxLength":512,"pattern":"^(https?://|mailto:|/)"}'::jsonb)
WHERE type = 'HERO' AND schema_version = 1;

UPDATE type_bloc
SET json_schema = jsonb_set(
        json_schema,
        '{properties,action,properties,href}',
        '{"type":"string","maxLength":512,"pattern":"^(https?://|mailto:|/)"}'::jsonb)
WHERE type = 'COUNTDOWN' AND schema_version = 1;

-- Le resserrement doit avoir mordu : sans cela la migration passerait au vert
-- en n'ayant rien changé, ce qui est le contraire d'un correctif.
DO $$
DECLARE manquants int;
BEGIN
    SELECT count(*) INTO manquants
    FROM type_bloc
    WHERE type IN ('HERO', 'COUNTDOWN')
      AND json_schema::text NOT LIKE '%^(https?://|mailto:|/)%';
    IF manquants > 0 THEN
        RAISE EXCEPTION 'le motif d''URL n''a pas été appliqué à % type(s) de bloc', manquants;
    END IF;
END $$;
