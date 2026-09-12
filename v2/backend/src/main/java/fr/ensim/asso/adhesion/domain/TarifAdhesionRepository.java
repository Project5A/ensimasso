package fr.ensim.asso.adhesion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarifAdhesionRepository extends JpaRepository<TarifAdhesion, UUID> {
    List<TarifAdhesion> findByCampagneId(UUID campagneId);
    Optional<TarifAdhesion> findByCampagneIdAndPublicCible(UUID campagneId, PublicCible publicCible);
}
