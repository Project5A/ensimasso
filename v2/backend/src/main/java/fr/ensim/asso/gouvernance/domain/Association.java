package fr.ensim.asso.gouvernance.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Identité permanente d'une association. Ne change jamais : porte le slug,
 * donc l'URL publique. Tout ce qui tourne chaque année vit sur {@link Mandat}.
 */
@Entity
@Table(name = "association")
public class Association {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_asso", nullable = false)
    private TypeAssociation typeAsso;

    @Column(name = "fondee_le")
    private LocalDate fondeeLe;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected Association() { }

    public Association(String slug, String nom, TypeAssociation typeAsso) {
        this.slug = slug;
        this.nom = nom;
        this.typeAsso = typeAsso;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getNom() { return nom; }
    public TypeAssociation getTypeAsso() { return typeAsso; }
    public LocalDate getFondeeLe() { return fondeeLe; }
    public OffsetDateTime getCreeLe() { return creeLe; }

    public void renommer(String nouveauNom) { this.nom = nouveauNom; }
    public void setFondeeLe(LocalDate d) { this.fondeeLe = d; }

    public enum TypeAssociation { BUREAU, CLUB, TECHNIQUE }
}
