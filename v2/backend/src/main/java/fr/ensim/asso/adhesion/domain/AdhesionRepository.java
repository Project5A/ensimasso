package fr.ensim.asso.adhesion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdhesionRepository extends JpaRepository<Adhesion, UUID> {

    Optional<Adhesion> findByPersonneIdAndAssociationIdAndCouvreAnneeCode(
            UUID personneId, UUID associationId, String couvreAnneeCode);

    Optional<Adhesion> findByPaiementRef(String paiementRef);

    List<Adhesion> findByAssociationIdAndCouvreAnneeCode(UUID associationId, String couvreAnneeCode);

    /**
     * La question que tout le reste du système pose : cette personne est-elle
     * adhérente <em>en ce moment</em> ?
     *
     * <p>Aucune notion d'expiration : une adhésion appartient à une année, et
     * l'année en cours est déduite de la date. Un achat de juillet pour l'année
     * suivante n'est simplement pas encore courant, et le devient tout seul.
     */
    @Query("""
           select count(a) > 0 from Adhesion a
            where a.personneId = :personne
              and a.associationId = :association
              and a.couvreAnneeCode = :anneeCourante
              and a.statut = fr.ensim.asso.adhesion.domain.StatutAdhesion.ACTIVE
           """)
    boolean estAdherent(@Param("personne") UUID personneId,
                        @Param("association") UUID associationId,
                        @Param("anneeCourante") String anneeCourante);

    @Query("""
           select a from Adhesion a
            where a.personneId = :personne
              and a.statut = fr.ensim.asso.adhesion.domain.StatutAdhesion.ACTIVE
            order by a.couvreAnneeCode desc
           """)
    List<Adhesion> adhesionsActivesDe(@Param("personne") UUID personneId);
}
