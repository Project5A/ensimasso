package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une version de page. Publier ne modifie jamais une version : cela en crée
 * une nouvelle et archive la précédente.
 *
 * <p>Trois garanties sont imposées par la base plutôt que par ce code —
 * au plus une version {@code PUBLIEE} par page (index unique partiel), les
 * blocs d'une version publiée sont immuables (trigger), et les transitions de
 * statut sont restreintes à {@code BROUILLON → PUBLIEE → ARCHIVEE} (trigger).
 * Les méthodes ci-dessous expriment la même chose côté domaine, mais ce n'est
 * pas sur elles que repose la garantie.
 */
@Entity
@Table(name = "page_version")
public class PageVersion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "page_id", nullable = false, updatable = false)
    private UUID pageId;

    @Column(nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutVersion statut;

    @Column(name = "cree_par", nullable = false, updatable = false)
    private UUID creePar;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    @Column(name = "publie_par")
    private UUID publiePar;

    @Column(name = "publie_le")
    private OffsetDateTime publieLe;

    @Column
    private String note;

    protected PageVersion() { }

    public PageVersion(UUID pageId, int numero, UUID creePar) {
        this.pageId = pageId;
        this.numero = numero;
        this.creePar = creePar;
        this.statut = StatutVersion.BROUILLON;
    }

    public void publier(UUID par, OffsetDateTime quand) {
        if (statut != StatutVersion.BROUILLON) {
            throw new IllegalStateException("seul un brouillon peut être publié (statut : " + statut + ")");
        }
        this.statut = StatutVersion.PUBLIEE;
        this.publiePar = par;
        this.publieLe = quand;
    }

    public void archiver() {
        if (statut != StatutVersion.PUBLIEE) {
            throw new IllegalStateException("seule une version publiée peut être archivée (statut : " + statut + ")");
        }
        this.statut = StatutVersion.ARCHIVEE;
    }

    public boolean estModifiable() { return statut == StatutVersion.BROUILLON; }

    public UUID getId() { return id; }
    public UUID getPageId() { return pageId; }
    public int getNumero() { return numero; }
    public StatutVersion getStatut() { return statut; }
    public UUID getCreePar() { return creePar; }
    public OffsetDateTime getCreeLe() { return creeLe; }
    public UUID getPubliePar() { return publiePar; }
    public OffsetDateTime getPublieLe() { return publieLe; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
