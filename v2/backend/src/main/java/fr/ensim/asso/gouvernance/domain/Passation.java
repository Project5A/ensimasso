package fr.ensim.asso.gouvernance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** La passation, modélisée comme un agrégat avec sa machine à états. */
@Entity
@Table(name = "passation")
public class Passation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "mandat_sortant_id", updatable = false)
    private UUID mandatSortantId;

    @Column(name = "mandat_entrant_id", nullable = false, updatable = false)
    private UUID mandatEntrantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPassation statut;

    @Column(name = "preparee_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime prepareeLe;

    @Column(name = "preparee_par", nullable = false, updatable = false)
    private UUID prepareePar;

    @Column(name = "activee_le")
    private OffsetDateTime activeeLe;

    @Column(name = "pages_clonees", nullable = false)
    private int pagesClonees;

    protected Passation() { }

    public Passation(UUID associationId, UUID mandatSortantId, UUID mandatEntrantId,
                     UUID prepareePar, int pagesClonees) {
        this.associationId = associationId;
        this.mandatSortantId = mandatSortantId;
        this.mandatEntrantId = mandatEntrantId;
        this.prepareePar = prepareePar;
        this.pagesClonees = pagesClonees;
        this.statut = StatutPassation.PREPAREE;
    }

    /** Le bureau entrant est complet : la passation peut être activée. */
    public void marquerBureauComplete() {
        if (statut != StatutPassation.PREPAREE) {
            throw new IllegalStateException("passation déjà " + statut);
        }
        this.statut = StatutPassation.BUREAU_COMPLETE;
    }

    public void activer(OffsetDateTime quand) {
        if (statut != StatutPassation.BUREAU_COMPLETE) {
            throw new IllegalStateException(
                    "le bureau entrant doit être complet avant activation (statut : " + statut + ")");
        }
        this.statut = StatutPassation.ACTIVEE;
        this.activeeLe = quand;
    }

    /** Tant qu'elle n'est pas activée, une passation ratée est annulable. */
    public void annuler() {
        if (statut == StatutPassation.ACTIVEE) {
            throw new IllegalStateException("une passation activée ne peut plus être annulée");
        }
        this.statut = StatutPassation.ANNULEE;
    }

    public UUID getId() { return id; }
    public UUID getAssociationId() { return associationId; }
    public UUID getMandatSortantId() { return mandatSortantId; }
    public UUID getMandatEntrantId() { return mandatEntrantId; }
    public StatutPassation getStatut() { return statut; }
    public OffsetDateTime getPrepareeLe() { return prepareeLe; }
    public UUID getPrepareePar() { return prepareePar; }
    public OffsetDateTime getActiveeLe() { return activeeLe; }
    public int getPagesClonees() { return pagesClonees; }

    public enum StatutPassation { PREPAREE, BUREAU_COMPLETE, ACTIVEE, ANNULEE }
}
