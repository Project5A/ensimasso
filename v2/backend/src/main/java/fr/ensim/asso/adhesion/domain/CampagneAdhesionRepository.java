package fr.ensim.asso.adhesion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampagneAdhesionRepository extends JpaRepository<CampagneAdhesion, UUID> {
    Optional<CampagneAdhesion> findByAssociationIdAndCouvreAnneeCode(UUID associationId, String anneeCode);
    List<CampagneAdhesion> findByAssociationIdOrderByCouvreAnneeCodeDesc(UUID associationId);
}
