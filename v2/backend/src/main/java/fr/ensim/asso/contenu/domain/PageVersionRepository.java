package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageVersionRepository extends JpaRepository<PageVersion, UUID> {

    @Query("""
           select v from PageVersion v
            where v.pageId = :page
              and v.statut = fr.ensim.asso.contenu.domain.StatutVersion.PUBLIEE
           """)
    Optional<PageVersion> versionPubliee(@Param("page") UUID pageId);

    @Query("""
           select v from PageVersion v
            where v.pageId = :page
              and v.statut = fr.ensim.asso.contenu.domain.StatutVersion.BROUILLON
            order by v.numero desc
           """)
    List<PageVersion> brouillons(@Param("page") UUID pageId);

    @Query("select coalesce(max(v.numero), 0) from PageVersion v where v.pageId = :page")
    int dernierNumero(@Param("page") UUID pageId);

    List<PageVersion> findByPageIdOrderByNumeroDesc(UUID pageId);
}
