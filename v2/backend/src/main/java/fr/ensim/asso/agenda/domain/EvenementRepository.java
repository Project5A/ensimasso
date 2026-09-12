package fr.ensim.asso.agenda.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvenementRepository extends JpaRepository<Evenement, UUID> {

    List<Evenement> findByMandatIdOrderByDebutLeAsc(UUID mandatId);

    List<Evenement> findByMandatIdAndStatutInOrderByDebutLeAsc(
            UUID mandatId, Collection<StatutEvenement> statuts);

    Optional<Evenement> findByMandatIdAndSlug(UUID mandatId, String slug);

    boolean existsByMandatIdAndSlug(UUID mandatId, String slug);
}
