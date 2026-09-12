package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface MediaUsageRepository extends JpaRepository<MediaUsage, MediaUsage.Cle> {

    /** Un média référencé par une version figée ne peut pas être supprimé. */
    @Query("""
           select count(u) from MediaUsage u
            join PageVersion v on v.id = u.pageVersionId
            where u.mediaKey = :cle
              and v.statut <> fr.ensim.asso.contenu.domain.StatutVersion.BROUILLON
           """)
    long comptageDansVersionsFigees(@Param("cle") String mediaKey);
}
