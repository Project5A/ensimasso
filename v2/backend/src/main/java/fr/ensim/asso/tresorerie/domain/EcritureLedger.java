package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une écriture du journal comptable. Append-only, imposé par un trigger :
 * ni modification ni suppression, jamais.
 *
 * <p>Ce n'est pas de la comptabilité en partie double complète. C'est la trace
 * immuable de ce qui est entré et sorti, en regard de quoi — assez pour qu'un
 * trésorier réponde « d'où vient cette somme ? » deux ans après, y compris
 * quand le bureau a changé deux fois entre-temps.
 */
@Entity
@Table(name = "ecriture_ledger")
public class EcritureLedger {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "commande_id", nullable = false, updatable = false)
    private UUID commandeId;

    @Column(name = "paiement_id", updatable = false)
    private UUID paiementId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Sens sens;

    @Column(name = "montant_cents", nullable = false, updatable = false)
    private int montantCents;

    @Column(nullable = false, updatable = false)
    private String motif;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected EcritureLedger() { }

    public EcritureLedger(UUID commandeId, UUID paiementId, UUID associationId,
                          Sens sens, int montantCents, String motif) {
        if (montantCents <= 0) {
            throw new IllegalArgumentException("une écriture porte un montant strictement positif ; "
                    + "le sens est exprimé par ENTREE/SORTIE, pas par un signe");
        }
        this.commandeId = commandeId;
        this.paiementId = paiementId;
        this.associationId = associationId;
        this.sens = sens;
        this.montantCents = montantCents;
        this.motif = motif;
    }

    public UUID getId() { return id; }
    public UUID getCommandeId() { return commandeId; }
    public UUID getPaiementId() { return paiementId; }
    public UUID getAssociationId() { return associationId; }
    public Sens getSens() { return sens; }
    public int getMontantCents() { return montantCents; }
    public String getMotif() { return motif; }
    public OffsetDateTime getCreeLe() { return creeLe; }

    public enum Sens { ENTREE, SORTIE }
}
