package fr.ensim.asso.tresorerie.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LigneCommandeRepository extends JpaRepository<LigneCommande, UUID> {
    List<LigneCommande> findByCommandeId(UUID commandeId);
    boolean existsByTypeLigneAndReferenceId(TypeLigne typeLigne, UUID referenceId);
}
