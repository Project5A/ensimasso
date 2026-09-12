package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Où un média est-il utilisé ?
 *
 * <p>Les {@code mediaKey} vivent dans du JSONB : aucune intégrité
 * référentielle n'est possible. Cette table, écrite à la publication, répond
 * à la question et permet de refuser la suppression d'un média référencé par
 * une version publiée ou archivée — sans quoi supprimer une image casse
 * silencieusement des pages d'archive.
 */
@Entity
@Table(name = "media_usage")
@IdClass(MediaUsage.Cle.class)
public class MediaUsage {

    @Id
    @Column(name = "media_key", nullable = false, updatable = false)
    private String mediaKey;

    @Id
    @Column(name = "page_version_id", nullable = false, updatable = false)
    private UUID pageVersionId;

    protected MediaUsage() { }

    public MediaUsage(String mediaKey, UUID pageVersionId) {
        this.mediaKey = mediaKey;
        this.pageVersionId = pageVersionId;
    }

    public String getMediaKey() { return mediaKey; }
    public UUID getPageVersionId() { return pageVersionId; }

    public static class Cle implements Serializable {
        private String mediaKey;
        private UUID pageVersionId;

        public Cle() { }
        public Cle(String mediaKey, UUID pageVersionId) {
            this.mediaKey = mediaKey;
            this.pageVersionId = pageVersionId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cle c)) return false;
            return Objects.equals(mediaKey, c.mediaKey)
                && Objects.equals(pageVersionId, c.pageVersionId);
        }
        @Override public int hashCode() { return Objects.hash(mediaKey, pageVersionId); }
    }
}
