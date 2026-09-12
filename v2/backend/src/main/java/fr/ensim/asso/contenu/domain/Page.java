package fr.ensim.asso.contenu.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Une page d'un mandat. Le contenu réel vit dans ses {@link PageVersion}. */
@Entity
@Table(name = "page")
public class Page {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Les pages appartiennent à un MANDAT, pas à une association : c'est ce
     *  qui fait exister l'archive par année sans code particulier. */
    @Column(name = "mandat_id", nullable = false, updatable = false)
    private UUID mandatId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String titre;

    @Column(name = "ordre_menu", nullable = false)
    private int ordreMenu;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected Page() { }

    public Page(UUID mandatId, String slug, String titre, int ordreMenu) {
        this.mandatId = mandatId;
        this.slug = slug;
        this.titre = titre;
        this.ordreMenu = ordreMenu;
    }

    public UUID getId() { return id; }
    public UUID getMandatId() { return mandatId; }
    public String getSlug() { return slug; }
    public String getTitre() { return titre; }
    public int getOrdreMenu() { return ordreMenu; }

    public void renommer(String titre) { this.titre = titre; }
    public void setOrdreMenu(int o) { this.ordreMenu = o; }
}
