package fr.ensim.asso.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByCle(String cle);

    List<MediaAsset> findByAssociationIdAndAnneeCodeOrderByCreeLeDesc(UUID associationId, String anneeCode);

    /**
     * Ce média est-il référencé par une version de page figée ?
     *
     * <p>Si oui, le supprimer casserait une archive. Les clés vivent dans du
     * JSONB, donc sans intégrité référentielle : c'est {@code media_usage},
     * écrite à la publication, qui rend la question répondable.
     */
    @Query(value = """
           select count(*) from media_usage u
             join page_version v on v.id = u.page_version_id
            where u.media_key = :cle and v.statut <> 'BROUILLON'
           """, nativeQuery = true)
    long utilisationsFigees(@Param("cle") String cle);
}
