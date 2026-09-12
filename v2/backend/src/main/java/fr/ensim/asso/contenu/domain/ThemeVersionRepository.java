package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ThemeVersionRepository extends JpaRepository<ThemeVersion, UUID> {

    @Query("""
           select t from ThemeVersion t
            where t.mandatId = :mandat
              and t.statut = fr.ensim.asso.contenu.domain.StatutVersion.PUBLIEE
           """)
    Optional<ThemeVersion> versionPubliee(@Param("mandat") UUID mandatId);

    @Query("select coalesce(max(t.numero), 0) from ThemeVersion t where t.mandatId = :mandat")
    int dernierNumero(@Param("mandat") UUID mandatId);
}
