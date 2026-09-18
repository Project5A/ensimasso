package fr.ensim.asso.tresorerie.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, UUID> {

    /**
     * La commande, VERROUILLÉE jusqu'à la fin de la transaction.
     *
     * <p>À réserver aux transitions qui décident d'un mouvement d'argent. Le
     * remboursement lisait la commande sans verrou, appelait le prestataire,
     * écrivait au journal, et ne vérifiait qu'ENSUITE que la commande était
     * bien remboursable. Deux remboursements lancés en même temps — un double
     * clic suffit — lisaient tous deux une commande PAYEE, écrivaient tous deux
     * une SORTIE, et se terminaient tous deux sans erreur : le journal comptait
     * deux fois un remboursement qui n'a eu lieu qu'une, et le solde de
     * l'association était faux d'autant. (La clé d'idempotence protégeait
     * l'argent chez Stripe ; elle ne protégeait pas le journal.)
     *
     * <p>Avec ce verrou, le second attend, relit la ligne écrite par le
     * premier, et se voit refuser en 409.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commande c where c.id = :id")
    Optional<Commande> findByIdPourEcriture(@Param("id") UUID id);

    Optional<Commande> findByIntentionRef(String intentionRef);
    List<Commande> findByPersonneIdOrderByCreeLeDesc(UUID personneId);
    List<Commande> findByAssociationIdAndStatutOrderByCreeLeDesc(UUID associationId, StatutCommande statut);
}
