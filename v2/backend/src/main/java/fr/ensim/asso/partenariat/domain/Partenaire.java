package fr.ensim.asso.partenariat.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

/** Un partenaire d'un mandat. */
@Entity
@Table(name = "partenaire")
public class Partenaire {

    /**
     * L'ordre d'affichage : niveau d'abord — dans l'ordre de déclaration de
     * l'énumération, pas l'ordre alphabétique, sinon ARGENT passerait devant OR
     * — puis le rang choisi par l'association, puis le nom pour que deux
     * partenaires de même rang ne changent pas de place d'un rendu à l'autre.
     */
    public static final Comparator<Partenaire> AFFICHAGE =
            Comparator.comparing(Partenaire::getNiveau)
                    .thenComparingInt(Partenaire::getOrdre)
                    .thenComparing(Partenaire::getNom, String.CASE_INSENSITIVE_ORDER);

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "mandat_id", nullable = false, updatable = false)
    private UUID mandatId;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiveauPartenaire niveau = NiveauPartenaire.SOUTIEN;

    /** Une CLÉ d'objet, jamais une URL. */
    @Column(name = "logo_media_key")
    private String logoMediaKey;

    @Column(name = "url")
    private String url;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected Partenaire() { }

    public Partenaire(UUID mandatId, String nom, NiveauPartenaire niveau) {
        this.mandatId = mandatId;
        this.nom = nom;
        this.niveau = niveau;
    }

    public UUID getId() { return id; }
    public UUID getMandatId() { return mandatId; }
    public String getNom() { return nom; }
    public NiveauPartenaire getNiveau() { return niveau; }
    public String getLogoMediaKey() { return logoMediaKey; }
    public String getUrl() { return url; }
    public int getOrdre() { return ordre; }
    public boolean isVisible() { return visible; }

    public void decrire(String nom, NiveauPartenaire niveau, String logoMediaKey,
                        String url, int ordre, boolean visible) {
        this.nom = nom;
        this.niveau = niveau;
        this.logoMediaKey = logoMediaKey;
        this.url = url;
        this.ordre = ordre;
        this.visible = visible;
    }
}
