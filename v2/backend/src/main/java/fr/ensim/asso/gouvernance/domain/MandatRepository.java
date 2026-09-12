package fr.ensim.asso.gouvernance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandatRepository extends JpaRepository<Mandat, UUID> {

    Optional<Mandat> findByAssociationIdAndStatut(UUID associationId, StatutMandat statut);

    Optional<Mandat> findByAssociationIdAndAnneeCode(UUID associationId, String anneeCode);

    List<Mandat> findByAssociationIdOrderByDebutLeDesc(UUID associationId);

    /**
     * Le mandat en fonction d'une association. Aucun drapeau « actif » n'est
     * maintenu : la question est posée à la période elle-même.
     */
    @Query("""
           select m from Mandat m
            where m.associationId = :asso
              and m.statut = fr.ensim.asso.gouvernance.domain.StatutMandat.EN_FONCTION
           """)
    Optional<Mandat> mandatEnFonction(@Param("asso") UUID associationId);
    /** Combien d'associations sont réellement dirigées. Sert la métrique dont
     *  la chute à zéro signifie qu'aucune page publique ne s'affiche plus. */
    long countByStatut(StatutMandat statut);

}
