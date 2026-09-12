package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Trace d'un évènement reçu du prestataire.
 *
 * <p>La clé primaire est l'identifiant de l'évènement lui-même. Stripe réessaie
 * ses livraisons : insérer cette ligne <em>avant</em> de traiter rend le rejeu
 * inoffensif au niveau de la base, sans dépendre du soin du code consommateur.
 * La revue d'architecture avait relevé exactement ce point — le consommateur de
 * paiement est idempotent parce qu'on y a pensé, celui d'e-mail ne l'est pas
 * parce qu'il tient en une ligne.
 */
@Entity
@Table(name = "evenement_stripe")
public class EvenementStripe {

    @Id
    @Column(updatable = false)
    private String id;                    // « evt_… »

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(name = "recu_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime recuLe;

    @Column(name = "traite_le")
    private OffsetDateTime traiteLe;

    @Column
    private String resultat;

    protected EvenementStripe() { }

    public EvenementStripe(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public void marquerTraite(String resultat, OffsetDateTime quand) {
        this.resultat = resultat;
        this.traiteLe = quand;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public OffsetDateTime getTraiteLe() { return traiteLe; }
    public String getResultat() { return resultat; }
}
