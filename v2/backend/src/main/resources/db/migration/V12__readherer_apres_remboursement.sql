-- Un remboursement fermait l'année à l'étudiant, définitivement.
--
-- `adhesion` portait UNIQUE (personne_id, association_id, couvre_annee_code),
-- sans aucune condition sur le statut. La ligne d'une adhésion REMBOURSEE
-- continuait donc d'occuper la place : la personne ne pouvait plus JAMAIS
-- adhérer à cette association pour cette année-là. Pas jusqu'à la fin de la
-- campagne — jamais, l'unicité ne s'effaçant pas.
--
-- Le chemin est réel et court : la trésorerie rembourse une commande,
-- ServiceAdhesion.revoquerPourRemboursement passe l'adhésion en REMBOURSEE
-- (c'est exactement ce qu'elle doit faire : l'association rend l'argent et
-- reprend le droit), et la personne qui voulait simplement corriger son tarif
-- ou repayer se voit répondre « vous avez déjà une adhésion pour 2025-2026
-- (statut : REMBOURSEE) ». Le seul remède était un DELETE à la main en base.
--
-- Le commentaire d'origine disait ce que la contrainte devait servir : « le
-- filet qui rend impossible un double grant ». C'est un filet sur les
-- adhésions VIVANTES. Une adhésion remboursée ne donne aucun droit — elle n'a
-- rien à réserver.
--
-- L'unicité devient donc partielle, sur les deux seuls statuts qui occupent
-- réellement la place :
--   ACTIVE               — le droit est accordé ;
--   EN_ATTENTE_PAIEMENT  — une intention de paiement est en cours, en ouvrir
--                          une seconde reviendrait à pouvoir payer deux fois.
-- ANNULEE et REMBOURSEE ne réservent plus rien, et s'accumulent sans gêner :
-- c'est aussi l'historique qu'un trésorier doit pouvoir relire.
--
-- Le filet reste entier : deux ACTIVE restent impossibles, et une ACTIVE
-- interdit toujours d'en ouvrir une seconde en attente de paiement. Les trois
-- cas sont vérifiés plus bas, à l'endroit même où la règle est posée.

DO $$
DECLARE
    nom text;
BEGIN
    -- La contrainte était anonyme : PostgreSQL l'a nommée lui-même. On la
    -- retrouve par ses colonnes plutôt que par un nom deviné, pour que cette
    -- migration ne dépende pas d'une convention de nommage.
    SELECT c.conname INTO nom
      FROM pg_constraint c
     WHERE c.conrelid = 'adhesion'::regclass
       AND c.contype  = 'u'
       AND (SELECT array_agg(a.attname::text ORDER BY a.attname::text)
              FROM unnest(c.conkey) k
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k)
           = ARRAY['association_id','couvre_annee_code','personne_id'];

    IF nom IS NULL THEN
        RAISE EXCEPTION
            'contrainte d''unicité (personne, association, année) introuvable sur adhesion : '
            'cette migration ne sait pas ce qu''elle remplace';
    END IF;

    EXECUTE format('ALTER TABLE adhesion DROP CONSTRAINT %I', nom);
END $$;

CREATE UNIQUE INDEX adhesion_une_vivante_par_annee
    ON adhesion (personne_id, association_id, couvre_annee_code)
 WHERE statut IN ('ACTIVE', 'EN_ATTENTE_PAIEMENT');

COMMENT ON INDEX adhesion_une_vivante_par_annee IS
  'Une seule adhésion VIVANTE par personne, association et année couverte.
   Partielle à dessein : une adhésion ANNULEE ou REMBOURSEE ne donne aucun
   droit, donc ne réserve pas la place — sans quoi un remboursement fermerait
   l''année à l''étudiant pour toujours.';

-- -------------------------------------------------------------------------
-- La règle doit MORDRE, et ne mordre que là. Trois cas, joués pour de vrai.
-- -------------------------------------------------------------------------
DO $$
DECLARE
    asso     uuid := '00000000-0000-0000-0000-0000000000a1';
    mand     uuid := '00000000-0000-0000-0000-0000000000b1';
    personne uuid := '00000000-0000-0000-0000-0000000000c1';
BEGIN
    INSERT INTO association (id, slug, nom, type_asso)
    VALUES (asso, 'verif-v12', 'Vérification V12', 'BUREAU');
    INSERT INTO annee_universitaire (code, debut, fin)
    VALUES ('9970-9971', '9970-09-01', '9971-08-31');
    INSERT INTO mandat (id, association_id, annee_code, debut_le, statut)
    VALUES (mand, asso, '9970-9971', '9970-09-01T00:00:00Z', 'EN_FONCTION');

    -- 1. Deux adhésions VIVANTES pour la même année : refusé.
    INSERT INTO adhesion (personne_id, association_id, couvre_annee_code,
                          vendue_par_mandat_id, montant_paye_cents, statut)
    VALUES (personne, asso, '9970-9971', mand, 1000, 'ACTIVE');
    BEGIN
        INSERT INTO adhesion (personne_id, association_id, couvre_annee_code,
                              vendue_par_mandat_id, montant_paye_cents, statut)
        VALUES (personne, asso, '9970-9971', mand, 1000, 'EN_ATTENTE_PAIEMENT');
        RAISE EXCEPTION 'une seconde adhésion vivante a été acceptée : le filet ne mord plus';
    EXCEPTION
        WHEN unique_violation THEN
            NULL;   -- attendu
    END;

    -- 2. Une fois REMBOURSEE, la place se libère : c'est TOUT l'objet de cette
    --    migration, et le cas qui était impossible avant elle.
    UPDATE adhesion SET statut = 'REMBOURSEE'
     WHERE personne_id = personne AND association_id = asso;
    INSERT INTO adhesion (personne_id, association_id, couvre_annee_code,
                          vendue_par_mandat_id, montant_paye_cents, statut)
    VALUES (personne, asso, '9970-9971', mand, 1000, 'ACTIVE');

    -- 3. Et la nouvelle adhésion, elle, réserve bien la place à son tour.
    BEGIN
        INSERT INTO adhesion (personne_id, association_id, couvre_annee_code,
                              vendue_par_mandat_id, montant_paye_cents, statut)
        VALUES (personne, asso, '9970-9971', mand, 1000, 'ACTIVE');
        RAISE EXCEPTION 'la place n''est plus réservée du tout : le filet a disparu';
    EXCEPTION
        WHEN unique_violation THEN
            NULL;   -- attendu
    END;

    DELETE FROM adhesion WHERE association_id = asso;
    DELETE FROM mandat   WHERE id = mand;
    DELETE FROM association WHERE id = asso;
    DELETE FROM annee_universitaire WHERE code = '9970-9971';
END $$;
