package fr.ensim.asso.gouvernance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un membre du bureau pour un mandat donné.
 *
 * <p>Ces lignes sont à la fois la source des <em>droits</em> et une donnée
 * <em>métier affichée</em> (le trombinoscope, avec son ordre et ses titres
 * personnalisés). C'est précisément pour cela qu'elles vivent ici et non dans
 * des groupes Keycloak : sinon « affiche-moi le bureau » deviendrait un appel
 * à l'API d'administration de l'IdP.
 */
@Entity
@Table(name = "membre_bureau")
public class MembreBureau {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "mandat_id", nullable = false, updatable = false)
    private UUID mandatId;

    /** « sub » Keycloak — l'identité vit dans l'IdP, pas ici. */
    @Column(name = "personne_id", nullable = false, updatable = false)
    private UUID personneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Poste poste;

    @Column(name = "titre_affiche")
    private String titreAffiche;

    @Column(nullable = false)
    private int ordre;

    /** CLÉ d'objet, jamais une URL signée : c'est le bug STOR-01 de la v1. */
    @Column(name = "photo_media_key")
    private String photoMediaKey;

    @Column(name = "visible_public", nullable = false)
    private boolean visiblePublic = true;

    @Column(name = "nomme_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime nommeLe;

    @Column(name = "revoque_le")
    private OffsetDateTime revoqueLe;

    protected MembreBureau() { }

    public MembreBureau(UUID mandatId, UUID personneId, Poste poste, int ordre) {
        this.mandatId = mandatId;
        this.personneId = personneId;
        this.poste = poste;
        this.ordre = ordre;
    }

    public void revoquer(OffsetDateTime quand) { this.revoqueLe = quand; }
    public boolean estActif() { return revoqueLe == null; }

    public UUID getId() { return id; }
    public UUID getMandatId() { return mandatId; }
    public UUID getPersonneId() { return personneId; }
    public Poste getPoste() { return poste; }
    public String getTitreAffiche() { return titreAffiche; }
    public int getOrdre() { return ordre; }
    public String getPhotoMediaKey() { return photoMediaKey; }
    public boolean isVisiblePublic() { return visiblePublic; }
    public OffsetDateTime getRevoqueLe() { return revoqueLe; }

    public void setTitreAffiche(String t) { this.titreAffiche = t; }
    public void setPhotoMediaKey(String k) { this.photoMediaKey = k; }
    public void setVisiblePublic(boolean v) { this.visiblePublic = v; }
    public void setOrdre(int o) { this.ordre = o; }
}
