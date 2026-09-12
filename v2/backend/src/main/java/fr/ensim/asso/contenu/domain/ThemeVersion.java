package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * L'identité visuelle d'un mandat, sous forme de jetons de design.
 *
 * <p>Versionnée comme une page, et pour la même raison : muter un thème sur
 * place réécrirait l'apparence des archives. Le thème est une liste de choix
 * encadrés (palette, appairage typographique, rayon), pas du CSS libre — le but
 * produit est « chaque asso a une page différente et aucune n'est cassée ».
 */
@Entity
@Table(name = "theme_version")
public class ThemeVersion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "mandat_id", nullable = false, updatable = false)
    private UUID mandatId;

    @Column(nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutVersion statut;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String tokens;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected ThemeVersion() { }

    public ThemeVersion(UUID mandatId, int numero, String tokens) {
        this.mandatId = mandatId;
        this.numero = numero;
        this.tokens = tokens;
        this.statut = StatutVersion.BROUILLON;
    }

    public void publier() {
        if (statut != StatutVersion.BROUILLON) {
            throw new IllegalStateException("seul un brouillon de thème peut être publié");
        }
        this.statut = StatutVersion.PUBLIEE;
    }

    public void archiver() { this.statut = StatutVersion.ARCHIVEE; }

    public UUID getId() { return id; }
    public UUID getMandatId() { return mandatId; }
    public int getNumero() { return numero; }
    public StatutVersion getStatut() { return statut; }
    public String getTokens() { return tokens; }
    public void remplacerTokens(String t) { this.tokens = t; }
}
