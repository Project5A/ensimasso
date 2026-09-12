package fr.ensim.asso.media.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un objet binaire déposé par une association.
 *
 * <p>Le champ {@code cle} est une clé d'objet, jamais une URL : une contrainte
 * {@code CHECK} en base refuse toute valeur ressemblant à une URL ou portant
 * une chaîne de requête, ce qui rend le bug STOR-01 de la v1 impossible à
 * réintroduire, même par un script d'import.
 *
 * <p>Le média est rattaché à une association <em>et</em> à une année : la
 * médiathèque se range donc toute seule par mandat, et une politique de cycle
 * de vie par année devient triviale.
 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "annee_code", nullable = false, updatable = false)
    private String anneeCode;

    @Column(nullable = false, unique = true, updatable = false)
    private String cle;

    @Column(name = "nom_original")
    private String nomOriginal;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    private Integer largeur;
    private Integer hauteur;
    private String blurhash;

    @Column(name = "texte_alternatif")
    private String texteAlternatif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutMedia statut;

    @Column(name = "depose_par", nullable = false, updatable = false)
    private UUID deposePar;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    @Column(name = "confirme_le")
    private OffsetDateTime confirmeLe;

    protected MediaAsset() { }

    public MediaAsset(UUID associationId, String anneeCode, String cle,
                      String nomOriginal, String contentType, UUID deposePar) {
        if (cle.startsWith("http") || cle.contains("?")) {
            throw new IllegalArgumentException(
                    "une clé d'objet n'est pas une URL — c'est le bug STOR-01 de la v1");
        }
        this.associationId = associationId;
        this.anneeCode = anneeCode;
        this.cle = cle;
        this.nomOriginal = nomOriginal;
        this.contentType = contentType;
        this.deposePar = deposePar;
        this.statut = StatutMedia.ATTENTE_DEPOT;
    }

    /** Le dépôt est confirmé : taille et type réels relevés dans le stockage. */
    public void confirmer(long tailleOctets, String contentTypeReel, OffsetDateTime quand) {
        if (statut != StatutMedia.ATTENTE_DEPOT) {
            throw new IllegalStateException("média déjà " + statut);
        }
        this.tailleOctets = tailleOctets;
        this.contentType = contentTypeReel;
        this.statut = StatutMedia.DISPONIBLE;
        this.confirmeLe = quand;
    }

    public void rejeter() { this.statut = StatutMedia.REJETE; }

    public void marquerSupprime() { this.statut = StatutMedia.SUPPRIME; }

    public boolean estDisponible() { return statut == StatutMedia.DISPONIBLE; }

    public void decrire(String texteAlternatif) { this.texteAlternatif = texteAlternatif; }

    public void renseignerDimensions(Integer largeur, Integer hauteur, String blurhash) {
        this.largeur = largeur;
        this.hauteur = hauteur;
        this.blurhash = blurhash;
    }

    public UUID getId() { return id; }
    public UUID getAssociationId() { return associationId; }
    public String getAnneeCode() { return anneeCode; }
    public String getCle() { return cle; }
    public String getNomOriginal() { return nomOriginal; }
    public String getContentType() { return contentType; }
    public Long getTailleOctets() { return tailleOctets; }
    public Integer getLargeur() { return largeur; }
    public Integer getHauteur() { return hauteur; }
    public String getBlurhash() { return blurhash; }
    public String getTexteAlternatif() { return texteAlternatif; }
    public StatutMedia getStatut() { return statut; }
    public UUID getDeposePar() { return deposePar; }
    public OffsetDateTime getCreeLe() { return creeLe; }
    public OffsetDateTime getConfirmeLe() { return confirmeLe; }
}
