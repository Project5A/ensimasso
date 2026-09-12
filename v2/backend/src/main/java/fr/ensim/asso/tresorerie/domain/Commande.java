package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une commande : ce qu'une personne achète à une association.
 *
 * <p>Le montant total n'est jamais fourni par le client. Il est calculé à
 * partir des lignes, elles-mêmes tarifées côté serveur, et un trigger différé
 * vérifie au COMMIT que l'en-tête égale la somme des lignes.
 */
@Entity
@Table(name = "commande")
public class Commande {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "personne_id", nullable = false, updatable = false)
    private UUID personneId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCommande statut;

    @Column(name = "montant_total_cents", nullable = false)
    private int montantTotalCents;

    @Column(nullable = false)
    private String devise;

    /** Identifiant de l'intention de paiement chez le prestataire. */
    @Column(name = "intention_ref")
    private String intentionRef;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    @Column(name = "payee_le")
    private OffsetDateTime payeeLe;

    protected Commande() { }

    public Commande(UUID personneId, UUID associationId, int montantTotalCents, String devise) {
        this.personneId = personneId;
        this.associationId = associationId;
        this.montantTotalCents = montantTotalCents;
        this.devise = devise;
        this.statut = StatutCommande.OUVERTE;
    }

    public void rattacherIntention(String ref) {
        if (statut != StatutCommande.OUVERTE) {
            throw new IllegalStateException("commande " + statut + " : intention non rattachable");
        }
        this.intentionRef = ref;
    }

    /**
     * Marque la commande payée.
     *
     * <p>Idempotente sur le même montant : un webhook rejoué ne doit pas
     * échouer. Un montant différent est en revanche un signal grave — le
     * prestataire dit avoir encaissé autre chose que ce qui a été vendu.
     */
    public void marquerPayee(int montantEncaisseCents, OffsetDateTime quand) {
        if (statut == StatutCommande.PAYEE) {
            if (montantEncaisseCents != montantTotalCents) {
                throw new IllegalStateException(
                        "commande déjà payée pour un montant différent (" + montantTotalCents
                      + " vs " + montantEncaisseCents + ")");
            }
            return;                              // rejeu du même évènement
        }
        if (statut != StatutCommande.OUVERTE) {
            throw new IllegalStateException("commande " + statut + " : paiement impossible");
        }
        if (montantEncaisseCents != montantTotalCents) {
            throw new IllegalStateException(
                    "montant encaissé (" + montantEncaisseCents + ") différent du montant dû ("
                  + montantTotalCents + ")");
        }
        this.statut = StatutCommande.PAYEE;
        this.payeeLe = quand;
    }

    public void annuler() {
        if (statut == StatutCommande.PAYEE || statut == StatutCommande.REMBOURSEE) {
            throw new IllegalStateException("une commande payée se rembourse, elle ne s'annule pas");
        }
        this.statut = StatutCommande.ANNULEE;
    }

    public void rembourser() {
        if (statut != StatutCommande.PAYEE) {
            throw new IllegalStateException("seule une commande payée peut être remboursée");
        }
        this.statut = StatutCommande.REMBOURSEE;
    }

    public boolean estPayee() { return statut == StatutCommande.PAYEE; }

    public UUID getId() { return id; }
    public UUID getPersonneId() { return personneId; }
    public UUID getAssociationId() { return associationId; }
    public StatutCommande getStatut() { return statut; }
    public int getMontantTotalCents() { return montantTotalCents; }
    public String getDevise() { return devise; }
    public String getIntentionRef() { return intentionRef; }
    public OffsetDateTime getCreeLe() { return creeLe; }
    public OffsetDateTime getPayeeLe() { return payeeLe; }
}
