package fr.ensim.asso.partenariat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Aucun {@code OrderBy} sur le niveau, volontairement : il est persisté en
 * chaîne, donc SQL le trierait par ordre alphabétique — ARGENT, BRONZE, OR,
 * SOUTIEN — ce qui placerait le partenaire principal en troisième position.
 * Le tri se fait dans le service, sur l'ordre de déclaration de l'énumération.
 */
public interface PartenaireRepository extends JpaRepository<Partenaire, UUID> {

    List<Partenaire> findByMandatId(UUID mandatId);

    List<Partenaire> findByMandatIdAndVisibleTrue(UUID mandatId);

    boolean existsByMandatIdAndNom(UUID mandatId, String nom);
}
