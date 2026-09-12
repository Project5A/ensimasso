-- =====================================================================
-- V6 — Trésorerie.
--
-- Corrige les trois failles PAY de la v1 :
--   PAY-01  l'adhésion était accordée par un System.out.println("Paiement
--           de 10€ validé") — aucun appel à Stripe, aucune trace.
--   PAY-02  /api/payment/confirm inscrivait un participant sans jamais
--           vérifier le paiement. Une requête non authentifiée suffisait.
--   PAY-03  le montant de l'intention de paiement venait du navigateur.
--
-- Ici le prix est calculé côté serveur à partir des lignes de commande, et
-- le droit n'est accordé que dans la transaction qui enregistre le paiement.
-- =====================================================================

CREATE TABLE commande (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    personne_id           uuid NOT NULL,
    association_id        uuid NOT NULL REFERENCES association(id) ON DELETE RESTRICT,
    statut                text NOT NULL CHECK (statut IN
                              ('OUVERTE','PAYEE','ANNULEE','REMBOURSEE')),
    montant_total_cents   int  NOT NULL CHECK (montant_total_cents >= 0),
    devise                text NOT NULL DEFAULT 'EUR' CHECK (devise ~ '^[A-Z]{3}$'),
    intention_ref         text,
    cree_le               timestamptz NOT NULL DEFAULT now(),
    payee_le              timestamptz,

    CONSTRAINT commande_paiement_coherent
        CHECK (statut <> 'PAYEE' OR payee_le IS NOT NULL)
);
CREATE INDEX commande_par_personne ON commande (personne_id);
CREATE INDEX commande_par_asso     ON commande (association_id, statut);
CREATE UNIQUE INDEX commande_intention_unique ON commande (intention_ref)
    WHERE intention_ref IS NOT NULL;

CREATE TABLE ligne_commande (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id     uuid NOT NULL REFERENCES commande(id) ON DELETE CASCADE,
    type_ligne      text NOT NULL CHECK (type_ligne IN ('ADHESION','BILLET')),
    reference_id    uuid NOT NULL,
    libelle         text NOT NULL,
    montant_cents   int  NOT NULL CHECK (montant_cents >= 0),

    -- Un même droit ne peut pas être facturé deux fois.
    UNIQUE (type_ligne, reference_id)
);
CREATE INDEX ligne_par_commande ON ligne_commande (commande_id);

-- INVARIANT COMPTABLE — le total d'une commande est la somme de ses lignes.
-- Différé : les lignes sont insérées après l'en-tête, la vérification a donc
-- lieu au COMMIT. C'est ce qui rend l'invariant vrai en permanence du point de
-- vue de toute transaction concurrente, plutôt que « vrai si le code y pense ».
CREATE FUNCTION tresorerie_total_coherent() RETURNS trigger AS $$
DECLARE
    v_commande uuid;
    v_total    int;
    v_somme    int;
BEGIN
    -- La même fonction sert deux tables aux colonnes différentes. PL/pgSQL
    -- résout les champs à l'exécution : référencer NEW.commande_id sur une
    -- ligne de « commande » échoue, même à l'intérieur d'un COALESCE.
    IF TG_TABLE_NAME = 'commande' THEN
        IF TG_OP = 'DELETE' THEN v_commande := OLD.id;
                            ELSE v_commande := NEW.id;  END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN v_commande := OLD.commande_id;
                            ELSE v_commande := NEW.commande_id; END IF;
    END IF;

    SELECT montant_total_cents INTO v_total FROM commande WHERE id = v_commande;
    IF v_total IS NULL THEN
        RETURN NULL;                          -- commande supprimée : rien à vérifier
    END IF;

    SELECT COALESCE(SUM(montant_cents), 0) INTO v_somme
      FROM ligne_commande WHERE commande_id = v_commande;

    IF v_total <> v_somme THEN
        RAISE EXCEPTION
            'total incoherent pour la commande % : entete % != somme des lignes %',
            v_commande, v_total, v_somme
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER commande_total_coherent
    AFTER INSERT OR UPDATE OR DELETE ON ligne_commande
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION tresorerie_total_coherent();

CREATE CONSTRAINT TRIGGER commande_entete_coherente
    AFTER INSERT OR UPDATE OF montant_total_cents ON commande
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION tresorerie_total_coherent();

-- Une commande payée est figée : ses lignes ne bougent plus, sinon le montant
-- encaissé et ce qui a été vendu divergent sans trace.
CREATE FUNCTION tresorerie_lignes_figees() RETURNS trigger AS $$
DECLARE v_statut text;
BEGIN
    SELECT statut INTO v_statut FROM commande
     WHERE id = COALESCE(NEW.commande_id, OLD.commande_id);
    IF v_statut IN ('PAYEE','REMBOURSEE') THEN
        RAISE EXCEPTION 'commande % : les lignes d''une commande % sont figees',
            COALESCE(NEW.commande_id, OLD.commande_id), v_statut
            USING ERRCODE = '23514';
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;

CREATE TRIGGER ligne_figee
    BEFORE INSERT OR UPDATE OR DELETE ON ligne_commande
    FOR EACH ROW EXECUTE FUNCTION tresorerie_lignes_figees();

-- ------------------------------------------------------------- paiements
CREATE TABLE paiement (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id     uuid NOT NULL REFERENCES commande(id) ON DELETE RESTRICT,
    fournisseur     text NOT NULL DEFAULT 'STRIPE',

    -- L'identifiant chez le prestataire. UNIQUE : un même paiement ne peut
    -- pas être enregistré deux fois, quoi que fasse le consommateur de webhook.
    reference       text NOT NULL,
    montant_cents   int  NOT NULL CHECK (montant_cents >= 0),
    devise          text NOT NULL DEFAULT 'EUR',
    statut          text NOT NULL CHECK (statut IN ('REUSSI','ECHOUE','REMBOURSE')),
    recu_le         timestamptz NOT NULL DEFAULT now(),

    UNIQUE (fournisseur, reference)
);
CREATE INDEX paiement_par_commande ON paiement (commande_id);

-- IDEMPOTENCE DU WEBHOOK, au niveau de la base.
-- Stripe réessaie ses livraisons. La clé primaire sur l'identifiant
-- d'évènement rend un rejeu inoffensif sans dépendre du soin du code.
CREATE TABLE evenement_stripe (
    id          text PRIMARY KEY,             -- « evt_… »
    type        text NOT NULL,
    recu_le     timestamptz NOT NULL DEFAULT now(),
    traite_le   timestamptz,
    resultat    text
);

-- ---------------------------------------------------------------- ledger
-- Journal append-only. Ce n'est pas de la comptabilité en partie double
-- complète : c'est la trace immuable de ce qui est entré et sorti, en regard
-- de quoi. Suffisant pour qu'un trésorier réponde « d'où vient cette somme ? ».
CREATE TABLE ecriture_ledger (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id     uuid NOT NULL REFERENCES commande(id) ON DELETE RESTRICT,
    paiement_id     uuid REFERENCES paiement(id) ON DELETE RESTRICT,
    association_id  uuid NOT NULL REFERENCES association(id) ON DELETE RESTRICT,
    sens            text NOT NULL CHECK (sens IN ('ENTREE','SORTIE')),
    montant_cents   int  NOT NULL CHECK (montant_cents > 0),
    motif           text NOT NULL,
    cree_le         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ledger_par_asso ON ecriture_ledger (association_id, cree_le DESC);

CREATE FUNCTION ledger_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'le journal comptable est append-only : ni modification ni suppression'
        USING ERRCODE = '23514';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_immuable
    BEFORE UPDATE OR DELETE ON ecriture_ledger
    FOR EACH ROW EXECUTE FUNCTION ledger_append_only();
