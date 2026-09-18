package fr.ensim.asso.adhesion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdhesionRepository extends JpaRepository<Adhesion, UUID> {

    /**
     * L'adhésion qui occupe RÉELLEMENT la place, s'il y en a une.
     *
     * <p>La méthode cherchait toute adhésion de ce triplet, quel que soit son
     * statut. Une adhésion REMBOURSEE suffisait donc à refuser une nouvelle
     * adhésion — pour toujours, puisqu'un statut ne se périme pas. Rembourser
     * un étudiant, c'est lui rendre son argent ET reprendre son droit ; ce
     * n'est pas lui fermer l'année.
     *
     * <p>L'{@code Optional} est tenu par la base : l'index partiel unique
     * {@code adhesion_une_vivante_par_annee} (V12) porte exactement ces deux
     * statuts. Sans lui, deux lignes correspondraient et Spring Data lèverait
     * {@code IncorrectResultSizeDataAccessException} — un 500 à chaque
     * tentative d'adhésion.
     */
    @Query("""
           select a from Adhesion a
            where a.personneId = :personne
              and a.associationId = :association
              and a.couvreAnneeCode = :annee
              and a.statut in (fr.ensim.asso.adhesion.domain.StatutAdhesion.ACTIVE,
                               fr.ensim.asso.adhesion.domain.StatutAdhesion.EN_ATTENTE_PAIEMENT)
           """)
    Optional<Adhesion> adhesionVivante(@Param("personne") UUID personneId,
                                       @Param("association") UUID associationId,
                                       @Param("annee") String couvreAnneeCode);

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
