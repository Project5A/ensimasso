package fr.ensim.asso.gouvernance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembreBureauRepository extends JpaRepository<MembreBureau, UUID> {

    @Query("""
           select m from MembreBureau m
            where m.mandatId = :mandat and m.revoqueLe is null
            order by m.ordre asc
           """)
    List<MembreBureau> membresActifs(@Param("mandat") UUID mandatId);

    @Query("""
           select m from MembreBureau m
            where m.mandatId = :mandat and m.personneId = :personne and m.revoqueLe is null
           """)
    Optional<MembreBureau> posteActif(@Param("mandat") UUID mandatId,
                                      @Param("personne") UUID personneId);

    /**
     * Tous les postes actifs d'une personne, tous mandats en fonction confondus.
     * Une seule requête : c'est ce qui remplace six projections de permissions
     * répliquées via un topic compacté.
     */
    @Query("""
           select m from MembreBureau m
            join Mandat d on d.id = m.mandatId
            where m.personneId = :personne
              and m.revoqueLe is null
              and d.statut = fr.ensim.asso.gouvernance.domain.StatutMandat.EN_FONCTION
           """)
    List<MembreBureau> postesActifsDe(@Param("personne") UUID personneId);
}
