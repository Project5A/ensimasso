package fr.ensim.asso.tresorerie.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, UUID> {
    Optional<Commande> findByIntentionRef(String intentionRef);
    List<Commande> findByPersonneIdOrderByCreeLeDesc(UUID personneId);
    List<Commande> findByAssociationIdAndStatutOrderByCreeLeDesc(UUID associationId, StatutCommande statut);
}
