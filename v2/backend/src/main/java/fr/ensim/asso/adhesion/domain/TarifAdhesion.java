package fr.ensim.asso.adhesion.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Le prix d'une adhésion, pour un public donné.
 *
 * <p>Cette table est la réponse à la faille PAY-03 de la v1 : le montant est
 * <em>lu ici</em> au moment de créer l'adhésion. Il n'est jamais accepté depuis
 * la requête, sinon un client peut poster {@code {"amount": 1}} et payer un
 * centime pour n'importe quoi.
 */
@Entity
@Table(name = "tarif_adhesion")
public class TarifAdhesion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "campagne_id", nullable = false, updatable = false)
    private UUID campagneId;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "montant_cents", nullable = false)
    private int montantCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_cible", nullable = false, updatable = false)
    private PublicCible publicCible;

    protected TarifAdhesion() { }

    public TarifAdhesion(UUID campagneId, String libelle, int montantCents, PublicCible publicCible) {
        if (montantCents < 0) {
            throw new IllegalArgumentException("un tarif ne peut pas être négatif");
        }
        this.campagneId = campagneId;
        this.libelle = libelle;
        this.montantCents = montantCents;
        this.publicCible = publicCible;
    }

    public UUID getId() { return id; }
    public UUID getCampagneId() { return campagneId; }
    public String getLibelle() { return libelle; }
    public int getMontantCents() { return montantCents; }
    public PublicCible getPublicCible() { return publicCible; }
}
