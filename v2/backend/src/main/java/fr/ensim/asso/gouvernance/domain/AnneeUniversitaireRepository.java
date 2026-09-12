package fr.ensim.asso.gouvernance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface AnneeUniversitaireRepository extends JpaRepository<AnneeUniversitaire, String> {

    @Query("select a from AnneeUniversitaire a where :jour between a.debut and a.fin")
    Optional<AnneeUniversitaire> anneeCouvrant(@Param("jour") LocalDate jour);
}
