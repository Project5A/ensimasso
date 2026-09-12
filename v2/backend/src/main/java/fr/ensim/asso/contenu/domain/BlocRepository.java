package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BlocRepository extends JpaRepository<Bloc, UUID> {
    List<Bloc> findByPageVersionIdOrderByOrdreAsc(UUID pageVersionId);
    long countByPageVersionId(UUID pageVersionId);
}
