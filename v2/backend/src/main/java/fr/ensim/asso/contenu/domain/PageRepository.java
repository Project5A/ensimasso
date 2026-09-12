package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findByMandatIdOrderByOrdreMenuAsc(UUID mandatId);
    Optional<Page> findByMandatIdAndSlug(UUID mandatId, String slug);
}
