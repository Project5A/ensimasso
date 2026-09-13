package fr.ensim.asso.adhesion.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * L'adhésion d'un étudiant à une association, pour une année donnée.
 *
 * <p>Deux colonnes portent la correction issue de la revue d'architecture :
 * <ul>
 *   <li>{@code couvreAnneeCode} — <strong>la vérité</strong>. C'est ce que lit
 *       le contrôle d'accès à l'entrée de la K-Fêt.</li>
 *   <li>{@code vendueParMandatId} — <strong>l'audit</strong>. Quel bureau a
 *       encaissé, ce qui n'est pas la même question.</li>
 * </ul>
 *
 * <p>Confondre les deux rend l'« early bird » de juillet impossible à
 * représenter : l'étudiant paie, puis se voit refuser l'entrée pendant six
 * semaines parce que l'année de son adhésion n'est pas encore l'année courante.
 *
 * <p>Une adhésion n'est jamais « révoquée » à la fin de l'année : elle
 * appartient simplement à une année qui s'est terminée. Il n'y a donc aucune
 * tâche d'expiration à faire tourner, ni à surveiller.
 */
@Entity
@Table(name = "adhesion")
public class Adhesion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "personne_id", nullable = false, updatable = false)
    private UUID personneId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "couvre_annee_code", nullable = false, updatable = false)
    private String couvreAnneeCode;

    @Column(name = "vendue_par_mandat_id", nullable = false, updatable = false)
    private UUID vendueParMandatId;

    @Column(name = "tarif_id")
    private UUID tarifId;

    /** Figé à la création depuis le tarif serveur — jamais fourni par le client. */
    @Column(name = "montant_paye_cents", nullable = false, updatable = false)
    private int montantPayeCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAdhesion statut;

    /**
     * Référence du paiement chez le prestataire. Un index unique partiel
     * garantit qu'une même référence ne peut activer deux adhésions : c'est le
     * filet d'idempotence du webhook, au niveau de la base.
     */
    @Column(name = "paiement_ref")
    private String paiementRef;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creeLe;

    @Column(name = "activee_le")
    private OffsetDateTime activeeLe;

    protected Adhesion() { }

    /**
     * @param quand l'instant de création, lu de l'horloge INJECTÉE par
     *        l'appelant. Le constructeur appelait {@code OffsetDateTime.now()}
     *        pour dater une adhésion gratuite : c'était le seul chemin du
     *        module à ignorer l'horloge, et donc le seul qu'un test à horloge
     *        fixe ne pouvait pas vérifier.
     */
    public Adhesion(UUID personneId, UUID associationId, String couvreAnneeCode,
                    UUID vendueParMandatId, UUID tarifId, int montantPayeCents,
                    OffsetDateTime quand) {
        this.personneId = personneId;
        this.associationId = associationId;
        this.couvreAnneeCode = couvreAnneeCode;
        this.vendueParMandatId = vendueParMandatId;
        this.tarifId = tarifId;
        this.montantPayeCents = montantPayeCents;
        this.statut = montantPayeCents == 0 ? StatutAdhesion.ACTIVE : StatutAdhesion.EN_ATTENTE_PAIEMENT;
        if (this.statut == StatutAdhesion.ACTIVE) {
            // `quand` et non OffsetDateTime.now() : c'était le seul chemin du
            // module à ignorer l'horloge injectée. Une adhésion gratuite se
            // datait donc à l'heure de la machine, sans qu'aucun test à horloge
            // fixe puisse le voir — et l'adhésion gratuite est précisément le
            // seul cas qui s'active sans repasser par le webhook.
            this.activeeLe = quand;
        }
    }

    /**
     * Activation sur confirmation du paiement. Idempotente : rejouer le même
     * évènement Stripe ne change rien, ce qui est exactement ce qu'un webhook
     * réessayé doit produire.
     */
    public void activer(String paiementRef, OffsetDateTime quand) {
        if (statut == StatutAdhesion.ACTIVE) {
            if (paiementRef != null && paiementRef.equals(this.paiementRef)) {
                return;                      // même paiement rejoué : sans effet
            }
            throw new IllegalStateException("adhésion déjà active avec un autre paiement");
        }
        if (statut != StatutAdhesion.EN_ATTENTE_PAIEMENT) {
            throw new IllegalStateException("adhésion " + statut + " : activation impossible");
        }
        this.statut = StatutAdhesion.ACTIVE;
        this.paiementRef = paiementRef;
        this.activeeLe = quand;
    }

    public void annuler() {
        if (statut == StatutAdhesion.ACTIVE) {
            throw new IllegalStateException("une adhésion active se rembourse, elle ne s'annule pas");
        }
        this.statut = StatutAdhesion.ANNULEE;
    }

    public void rembourser() {
        if (statut != StatutAdhesion.ACTIVE) {
            throw new IllegalStateException("seule une adhésion active peut être remboursée");
        }
        this.statut = StatutAdhesion.REMBOURSEE;
    }

    public boolean estActive() { return statut == StatutAdhesion.ACTIVE; }

    public UUID getId() { return id; }
    public UUID getPersonneId() { return personneId; }
    public UUID getAssociationId() { return associationId; }
    public String getCouvreAnneeCode() { return couvreAnneeCode; }
    public UUID getVendueParMandatId() { return vendueParMandatId; }
    public UUID getTarifId() { return tarifId; }
    public int getMontantPayeCents() { return montantPayeCents; }
    public StatutAdhesion getStatut() { return statut; }
    public String getPaiementRef() { return paiementRef; }
    public OffsetDateTime getCreeLe() { return creeLe; }
    public OffsetDateTime getActiveeLe() { return activeeLe; }
}
