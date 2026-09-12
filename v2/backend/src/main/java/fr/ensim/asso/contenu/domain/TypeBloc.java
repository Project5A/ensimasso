package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

/**
 * Une version d'un type de bloc.
 *
 * <p>Clé composite {@code (type, schemaVersion)} : une nouvelle version du
 * schéma ajoute une ligne, elle n'écrase jamais la précédente. Sans cela, on ne
 * peut plus revalider un bloc écrit en 2025, et un « upcaster » n'a aucun
 * contrat source à vérifier — c'est l'une des corrections issues de la revue.
 */
@Entity
@Table(name = "type_bloc")
@IdClass(TypeBloc.Cle.class)
public class TypeBloc {

    @Id
    @Column(nullable = false, updatable = false)
    private String type;

    @Id
    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_schema", nullable = false)
    private String jsonSchema;

    @Column(name = "composant_react", nullable = false)
    private String composantReact;

    @Column(nullable = false)
    private String categorie;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private boolean depreciee;

    protected TypeBloc() { }

    public String getType() { return type; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getJsonSchema() { return jsonSchema; }
    public String getComposantReact() { return composantReact; }
    public String getCategorie() { return categorie; }
    public String getLibelle() { return libelle; }
    public boolean isDepreciee() { return depreciee; }

    /** Clé composite. */
    public static class Cle implements Serializable {
        private String type;
        private int schemaVersion;

        public Cle() { }
        public Cle(String type, int schemaVersion) {
            this.type = type;
            this.schemaVersion = schemaVersion;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cle c)) return false;
            return schemaVersion == c.schemaVersion && Objects.equals(type, c.type);
        }
        @Override public int hashCode() { return Objects.hash(type, schemaVersion); }
    }
}
