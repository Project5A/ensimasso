package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Une ligne de commande : un droit précis, à un prix décidé par le serveur.
 *
 * <p>{@code UNIQUE (type_ligne, reference_id)} en base : un même droit ne peut
 * pas être facturé deux fois, même si deux requêtes concurrentes le tentent.
 */
@Entity
@Table(name = "ligne_commande")
public class LigneCommande {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "commande_id", nullable = false, updatable = false)
    private UUID commandeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_ligne", nullable = false, updatable = false)
    private TypeLigne typeLigne;

    /** L'identifiant du droit acheté : une adhésion, un billet. */
    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "montant_cents", nullable = false, updatable = false)
    private int montantCents;

    protected LigneCommande() { }

    public LigneCommande(UUID commandeId, TypeLigne typeLigne, UUID referenceId,
                         String libelle, int montantCents) {
        this.commandeId = commandeId;
        this.typeLigne = typeLigne;
        this.referenceId = referenceId;
        this.libelle = libelle;
        this.montantCents = montantCents;
    }

    public UUID getId() { return id; }
    public UUID getCommandeId() { return commandeId; }
    public TypeLigne getTypeLigne() { return typeLigne; }
    public UUID getReferenceId() { return referenceId; }
    public String getLibelle() { return libelle; }
    public int getMontantCents() { return montantCents; }
}
