package fr.ensim.asso.gouvernance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AssociationRepository extends JpaRepository<Association, UUID> {
    Optional<Association> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
