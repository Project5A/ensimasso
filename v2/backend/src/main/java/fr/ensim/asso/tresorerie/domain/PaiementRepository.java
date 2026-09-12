package fr.ensim.asso.tresorerie.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {
    Optional<Paiement> findByFournisseurAndReference(String fournisseur, String reference);
    List<Paiement> findByCommandeId(UUID commandeId);
}
