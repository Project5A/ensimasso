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
     * Tous les postes d'une personne sur un mandat qui accepte encore des
     * écritures. Une seule requête : c'est ce qui remplace six projections de
     * permissions répliquées via un topic compacté.
     *
     * <p>Elle ne rendait que les mandats EN_FONCTION. C'est la seule requête
     * dont le tableau de bord se sert pour savoir où l'utilisateur peut aller,
     * si bien que le bureau ENTRANT n'avait aucun chemin d'entrée : désigné
     * dans la passation, il lisait « Aucun mandat en cours », c'est-à-dire
     * qu'on lui demandait de se faire désigner alors qu'il venait de l'être.
     *
     * <p>PolitiqueAcces dit pourtant l'inverse, en toutes lettres : « le bureau
     * ENTRANT compose bien son site avant l'investiture ; c'est l'objet du
     * statut PRÉPARATION, et c'est pour cela qu'on ne le refuse pas ici ».
     * L'autorisation l'admettait, la navigation l'ignorait — et toute la
     * préparation (pages, thème cloné à la passation, agenda, aperçu) n'était
     * atteignable qu'en tapant un UUID à la main dans la barre d'adresse.
     *
     * <p>Le filtre est désormais le même que celui de l'autorisation, et pour
     * la même raison : {@code Mandat.accepteEcriture()}, c'est-à-dire tout sauf
     * CLOS. Deux règles qui répondent à la même question ne doivent pas pouvoir
     * diverger.
     */
    @Query("""
           select m from MembreBureau m
            join Mandat d on d.id = m.mandatId
            where m.personneId = :personne
              and m.revoqueLe is null
              and d.statut <> fr.ensim.asso.gouvernance.domain.StatutMandat.CLOS
            order by d.debutLe desc
           """)
    List<MembreBureau> postesActifsDe(@Param("personne") UUID personneId);
}
