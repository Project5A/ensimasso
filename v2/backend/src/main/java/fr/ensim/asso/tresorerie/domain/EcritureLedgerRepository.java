package fr.ensim.asso.tresorerie.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EcritureLedgerRepository extends JpaRepository<EcritureLedger, UUID> {

    List<EcritureLedger> findByAssociationIdOrderByCreeLeDesc(UUID associationId);

    /** Solde d'une association : entrées moins sorties, en centimes. */
    @Query("""
           select coalesce(sum(case when e.sens = fr.ensim.asso.tresorerie.domain.EcritureLedger$Sens.ENTREE
                                    then e.montantCents else -e.montantCents end), 0)
             from EcritureLedger e
            where e.associationId = :asso
           """)
    long solde(@Param("asso") UUID associationId);
}
