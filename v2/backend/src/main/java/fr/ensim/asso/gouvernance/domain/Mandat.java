package fr.ensim.asso.gouvernance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Le mandat d'un bureau : une <em>période</em>, pas une année.
 *
 * <p>C'est la correction centrale issue de la revue d'architecture. Les
 * élections de BDE/BDS ont lieu à l'AG, souvent au printemps. Une clé
 * {@code (association, année)} ne peut pas représenter une passation de juin :
 * il faudrait soit écraser le bureau en cours (et perdre la trace de qui a
 * réellement exercé), soit activer le mandat suivant trois mois trop tôt (et
 * invalider toutes les adhésions en cours).
 *
 * <p>La non-superposition est garantie par une contrainte d'exclusion GiST en
 * base, pas par du code applicatif.
 */
@Entity
@Table(name = "mandat")
public class Mandat {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    /** Étiquette lisible (« 2026-2027 »). N'est pas la clé. */
    @Column(name = "annee_code", nullable = false)
    private String anneeCode;

    @Column(name = "debut_le", nullable = false)
    private OffsetDateTime debutLe;

    /** {@code null} = fin non encore fixée (mandat en cours). */
    @Column(name = "fin_le")
    private OffsetDateTime finLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutMandat statut;

    @Column(name = "investi_le")
    private OffsetDateTime investiLe;

    @Column(name = "clos_le")
    private OffsetDateTime closLe;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    protected Mandat() { }

    private Mandat(UUID associationId, String anneeCode, OffsetDateTime debutLe,
                   OffsetDateTime finLe, StatutMandat statut) {
        this.associationId = associationId;
        this.anneeCode = anneeCode;
        this.debutLe = debutLe;
        this.finLe = finLe;
        this.statut = statut;
    }

    /** Mandat créé par une passation : invisible, période encore provisoire. */
    public static Mandat enPreparation(UUID associationId, String anneeCode,
                                       OffsetDateTime debutPrevu, OffsetDateTime finPrevue) {
        return new Mandat(associationId, anneeCode, debutPrevu, finPrevue, StatutMandat.PREPARATION);
    }

    /**
     * Investiture : le bureau prend ses fonctions à l'AG. C'est ici, et
     * seulement ici, que la contrainte d'exclusion devient active — un
     * chevauchement avec le mandat sortant est alors refusé par la base.
     */
    public void investir(OffsetDateTime aLAg) {
        if (statut != StatutMandat.PREPARATION) {
            throw new IllegalStateException(
                    "seul un mandat en préparation peut être investi (statut actuel : " + statut + ")");
        }
        this.debutLe = aLAg;
        this.statut = StatutMandat.EN_FONCTION;
        this.investiLe = aLAg;
    }

    /** Clôture : le mandat devient immuable, ce qui rend l'archive fiable. */
    public void clore(OffsetDateTime quand) {
        if (statut != StatutMandat.EN_FONCTION) {
            throw new IllegalStateException(
                    "seul un mandat en fonction peut être clos (statut actuel : " + statut + ")");
        }
        this.finLe = quand;
        this.closLe = quand;
        this.statut = StatutMandat.CLOS;
    }

    public boolean estEnFonctionA(OffsetDateTime instant) {
        return statut == StatutMandat.EN_FONCTION
                && !instant.isBefore(debutLe)
                && (finLe == null || instant.isBefore(finLe));
    }

    /** Un mandat clos ne peut plus rien accepter en écriture. */
    public boolean accepteEcriture() {
        return statut != StatutMandat.CLOS;
    }

    public UUID getId() { return id; }
    public UUID getAssociationId() { return associationId; }
    public String getAnneeCode() { return anneeCode; }
    public OffsetDateTime getDebutLe() { return debutLe; }
    public OffsetDateTime getFinLe() { return finLe; }
    public StatutMandat getStatut() { return statut; }
    public OffsetDateTime getInvestiLe() { return investiLe; }
    public OffsetDateTime getClosLe() { return closLe; }
    public OffsetDateTime getCreeLe() { return creeLe; }
}
