package fr.ensim.asso.agenda.domain;

import fr.ensim.asso.shared.error.Erreurs;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Un évènement organisé par un bureau, pendant son mandat. */
@Entity
@Table(name = "evenement")
public class Evenement {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Le mandat organisateur. Immuable : un évènement ne change pas de bureau. */
    @Column(name = "mandat_id", nullable = false, updatable = false)
    private UUID mandatId;

    @Column(nullable = false, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String titre;

    @Column(name = "resume")
    private String resume;

    @Column(name = "description")
    private String description;

    @Column(name = "lieu")
    private String lieu;

    @Column(name = "debut_le", nullable = false)
    private OffsetDateTime debutLe;

    @Column(name = "fin_le")
    private OffsetDateTime finLe;

    /** Une CLÉ d'objet, jamais une URL : l'URL est fabriquée à la lecture. */
    @Column(name = "media_key")
    private String mediaKey;

    @Column(name = "lien")
    private String lien;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEvenement statut = StatutEvenement.BROUILLON;

    @Column(nullable = false)
    private boolean complet;

    @Column(name = "annule_le")
    private OffsetDateTime annuleLe;

    @Column(name = "motif_annulation")
    private String motifAnnulation;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected Evenement() { }

    public Evenement(UUID mandatId, String slug, String titre, OffsetDateTime debutLe) {
        this.mandatId = mandatId;
        this.slug = slug;
        this.titre = titre;
        this.debutLe = debutLe;
    }

    public UUID getId() { return id; }
    public UUID getMandatId() { return mandatId; }
    public String getSlug() { return slug; }
    public String getTitre() { return titre; }
    public String getResume() { return resume; }
    public String getDescription() { return description; }
    public String getLieu() { return lieu; }
    public OffsetDateTime getDebutLe() { return debutLe; }
    public OffsetDateTime getFinLe() { return finLe; }
    public String getMediaKey() { return mediaKey; }
    public String getLien() { return lien; }
    public StatutEvenement getStatut() { return statut; }
    public boolean isComplet() { return complet; }
    public OffsetDateTime getAnnuleLe() { return annuleLe; }
    public String getMotifAnnulation() { return motifAnnulation; }

    /**
     * L'instant après lequel l'évènement est « passé » : sa fin s'il en a une,
     * son début sinon. Sans cette règle, un évènement d'une journée entière
     * disparaîtrait de l'agenda à l'heure de son ouverture.
     */
    public OffsetDateTime finEffective() {
        return finLe != null ? finLe : debutLe;
    }

    public boolean estPasse(OffsetDateTime maintenant) {
        return finEffective().isBefore(maintenant);
    }

    /** Visible du public : tout sauf un brouillon. Un évènement annulé se voit. */
    public boolean estPublic() {
        return statut != StatutEvenement.BROUILLON;
    }

    public void decrire(String titre, String resume, String description, String lieu,
                        OffsetDateTime debutLe, OffsetDateTime finLe,
                        String mediaKey, String lien, boolean complet) {
        if (finLe != null && !finLe.isAfter(debutLe)) {
            throw new Erreurs.RequeteInvalide("la fin d'un évènement doit suivre son début");
        }
        this.titre = titre;
        this.resume = resume;
        this.description = description;
        this.lieu = lieu;
        this.debutLe = debutLe;
        this.finLe = finLe;
        this.mediaKey = mediaKey;
        this.lien = lien;
        this.complet = complet;
    }

    public void publier() {
        if (statut == StatutEvenement.ANNULE) {
            throw new Erreurs.Conflit("un évènement annulé ne se republie pas");
        }
        this.statut = StatutEvenement.PUBLIE;
    }

    /**
     * Annule l'évènement. Irréversible, et c'est voulu : « réactiver » une
     * annulation déjà communiquée fabriquerait une information contradictoire
     * pour les gens qui l'ont lue. On en recrée un si besoin.
     */
    public void annuler(String motif, OffsetDateTime quand) {
        if (statut == StatutEvenement.ANNULE) {
            return;                       // idempotent : annuler deux fois n'est pas une erreur
        }
        this.statut = StatutEvenement.ANNULE;
        this.annuleLe = quand;
        this.motifAnnulation = motif;
    }
}
