package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Un encaissement chez le prestataire. {@code (fournisseur, reference)} est unique. */
@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "commande_id", nullable = false, updatable = false)
    private UUID commandeId;

    @Column(nullable = false, updatable = false)
    private String fournisseur;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "montant_cents", nullable = false, updatable = false)
    private int montantCents;

    @Column(nullable = false)
    private String devise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiement statut;

    @Column(name = "recu_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime recuLe;

    protected Paiement() { }

    public Paiement(UUID commandeId, String fournisseur, String reference,
                    int montantCents, String devise, StatutPaiement statut) {
        this.commandeId = commandeId;
        this.fournisseur = fournisseur;
        this.reference = reference;
        this.montantCents = montantCents;
        this.devise = devise;
        this.statut = statut;
    }

    public void marquerRembourse() { this.statut = StatutPaiement.REMBOURSE; }

    public UUID getId() { return id; }
    public UUID getCommandeId() { return commandeId; }
    public String getFournisseur() { return fournisseur; }
    public String getReference() { return reference; }
    public int getMontantCents() { return montantCents; }
    public String getDevise() { return devise; }
    public StatutPaiement getStatut() { return statut; }
    public OffsetDateTime getRecuLe() { return recuLe; }

    public enum StatutPaiement { REUSSI, ECHOUE, REMBOURSE }
}
