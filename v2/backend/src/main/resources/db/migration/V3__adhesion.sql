-- =====================================================================
-- V3 — Adhésions.
--
-- Correction issue de la revue : ce que l'adhésion COUVRE est distinct du
-- mandat qui l'a VENDUE. Sans cela, une campagne « early bird » ouverte en
-- juillet pour l'année suivante produit des adhésions dont l'année n'est pas
-- l'année courante — et le scan à l'entrée de la K-Fêt les refuse pendant
-- six semaines.
-- =====================================================================

CREATE TABLE campagne_adhesion (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id          uuid NOT NULL REFERENCES association(id) ON DELETE CASCADE,
    couvre_annee_code       text NOT NULL REFERENCES annee_universitaire(code),
    ouverte_par_mandat_id   uuid NOT NULL REFERENCES mandat(id),
    statut                  text NOT NULL CHECK (statut IN ('BROUILLON','OUVERTE','FERMEE')),
    ouvre_le                timestamptz,
    ferme_le                timestamptz,
    UNIQUE (association_id, couvre_annee_code),
    CONSTRAINT campagne_fenetre_coherente
        CHECK (ferme_le IS NULL OR ouvre_le IS NULL OR ferme_le > ouvre_le)
);
COMMENT ON COLUMN campagne_adhesion.couvre_annee_code IS
  'Année couverte par les adhésions vendues. Peut être postérieure à l''année
   en cours : c''est exactement le cas "early bird" de juillet.';

CREATE TABLE tarif_adhesion (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campagne_id     uuid NOT NULL REFERENCES campagne_adhesion(id) ON DELETE CASCADE,
    libelle         text NOT NULL,
    montant_cents   int  NOT NULL CHECK (montant_cents >= 0),
    public_cible    text NOT NULL CHECK (public_cible IN ('ETUDIANT','EXTERIEUR','ANCIEN')),
    UNIQUE (campagne_id, public_cible)
);
COMMENT ON TABLE tarif_adhesion IS
  'Le prix vit ici, côté serveur. Il n''est JAMAIS accepté depuis le client :
   c''est la correction structurelle de la faille PAY-03 de la v1.';

CREATE TABLE adhesion (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    personne_id             uuid NOT NULL,
    association_id          uuid NOT NULL REFERENCES association(id) ON DELETE RESTRICT,

    -- LA VÉRITÉ : ce que l'adhésion couvre. C'est ce que lit le contrôle d'accès.
    couvre_annee_code       text NOT NULL REFERENCES annee_universitaire(code),
    -- L'AUDIT : quel bureau a encaissé.
    vendue_par_mandat_id    uuid NOT NULL REFERENCES mandat(id),

    tarif_id                uuid REFERENCES tarif_adhesion(id),
    montant_paye_cents      int  NOT NULL CHECK (montant_paye_cents >= 0),
    statut                  text NOT NULL CHECK (statut IN
                                ('EN_ATTENTE_PAIEMENT','ACTIVE','ANNULEE','REMBOURSEE')),
    paiement_ref            text,
    cree_le                 timestamptz NOT NULL DEFAULT now(),
    activee_le              timestamptz,

    -- Un étudiant n'adhère qu'une fois par association et par année couverte.
    -- C'est aussi le filet qui rend impossible un double « grant » même si un
    -- consommateur d'évènement est bogué.
    UNIQUE (personne_id, association_id, couvre_annee_code)
);

CREATE INDEX adhesion_par_personne ON adhesion (personne_id) WHERE statut = 'ACTIVE';
CREATE INDEX adhesion_par_asso_annee ON adhesion (association_id, couvre_annee_code);
CREATE UNIQUE INDEX adhesion_paiement_unique ON adhesion (paiement_ref)
    WHERE paiement_ref IS NOT NULL;

-- est_adherent(p, a) = il existe une adhésion ACTIVE couvrant l'année en cours.
-- Un achat de juillet pour l'année suivante n'est simplement pas encore
-- courant : aucun cas particulier n'est nécessaire.
CREATE FUNCTION est_adherent(p_personne uuid, p_association uuid, p_a_la_date timestamptz DEFAULT now())
RETURNS boolean AS $$
    SELECT EXISTS (
        SELECT 1
          FROM adhesion a
          JOIN annee_universitaire an ON an.code = a.couvre_annee_code
         WHERE a.personne_id    = p_personne
           AND a.association_id = p_association
           AND a.statut         = 'ACTIVE'
           AND p_a_la_date::date BETWEEN an.debut AND an.fin
    );
$$ LANGUAGE sql STABLE;
