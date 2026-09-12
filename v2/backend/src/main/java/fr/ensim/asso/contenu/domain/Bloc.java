package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Un bloc d'une version de page.
 *
 * <p>Le contenu est du JSONB validé contre le JSON Schema de
 * {@code (type, schemaVersion)}. Le bloc pointe vers une version <em>précise</em>
 * du schéma, qui existe toujours : un bloc archivé reste donc revalidable des
 * années plus tard.
 *
 * <p>{@code RICH_TEXT} stocke un document structuré, jamais une chaîne HTML :
 * il n'y a ainsi aucun balisage à assainir, donc aucun XSS stocké possible.
 */
@Entity
@Table(name = "bloc")
public class Bloc {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "page_version_id", nullable = false, updatable = false)
    private UUID pageVersionId;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private boolean visible = true;

    protected Bloc() { }

    public Bloc(UUID pageVersionId, int ordre, String type, int schemaVersion, String payload) {
        this.pageVersionId = pageVersionId;
        this.ordre = ordre;
        this.type = type;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public UUID getPageVersionId() { return pageVersionId; }
    public int getOrdre() { return ordre; }
    public String getType() { return type; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getPayload() { return payload; }
    public boolean isVisible() { return visible; }

    public void deplacer(int nouvelOrdre) { this.ordre = nouvelOrdre; }
    public void remplacerPayload(String payload) { this.payload = payload; }
    public void setVisible(boolean v) { this.visible = v; }
}
