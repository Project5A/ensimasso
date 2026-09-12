package fr.ensim.asso.adhesion.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une campagne d'adhésion, ouverte par un bureau pour une année donnée.
 *
 * <p>La distinction décisive : {@code couvreAnneeCode} est l'année que les
 * adhésions vendues couvriront, {@code ouvertePparMandatId} est le bureau qui
 * l'a ouverte. Les deux diffèrent dès qu'une campagne « early bird » s'ouvre
 * en juillet pour l'année suivante — cas que la première version du modèle ne
 * savait pas représenter.
 */
@Entity
@Table(name = "campagne_adhesion")
public class CampagneAdhesion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "couvre_annee_code", nullable = false, updatable = false)
    private String couvreAnneeCode;

    @Column(name = "ouverte_par_mandat_id", nullable = false, updatable = false)
    private UUID ouvertePparMandatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCampagne statut;

    @Column(name = "ouvre_le")
    private OffsetDateTime ouvreLe;

    @Column(name = "ferme_le")
    private OffsetDateTime fermeLe;

    protected CampagneAdhesion() { }

    public CampagneAdhesion(UUID associationId, String couvreAnneeCode, UUID ouvertePparMandatId) {
        this.associationId = associationId;
        this.couvreAnneeCode = couvreAnneeCode;
        this.ouvertePparMandatId = ouvertePparMandatId;
        this.statut = StatutCampagne.BROUILLON;
    }

    public void ouvrir(OffsetDateTime quand, OffsetDateTime fermeture) {
        if (statut == StatutCampagne.FERMEE) {
            throw new IllegalStateException("une campagne fermée ne se rouvre pas");
        }
        this.statut = StatutCampagne.OUVERTE;
        this.ouvreLe = quand;
        this.fermeLe = fermeture;
    }

    public void fermer(OffsetDateTime quand) {
        this.statut = StatutCampagne.FERMEE;
        this.fermeLe = quand;
    }

    /** Accepte-t-elle des adhésions à cet instant ? */
    public boolean accepteAdhesionA(OffsetDateTime instant) {
        return statut == StatutCampagne.OUVERTE
                && (ouvreLe == null || !instant.isBefore(ouvreLe))
                && (fermeLe == null || instant.isBefore(fermeLe));
    }

    public UUID getId() { return id; }
    public UUID getAssociationId() { return associationId; }
    public String getCouvreAnneeCode() { return couvreAnneeCode; }
    public UUID getOuvertePparMandatId() { return ouvertePparMandatId; }
    public StatutCampagne getStatut() { return statut; }
    public OffsetDateTime getOuvreLe() { return ouvreLe; }
    public OffsetDateTime getFermeLe() { return fermeLe; }

    public enum StatutCampagne { BROUILLON, OUVERTE, FERMEE }
}
