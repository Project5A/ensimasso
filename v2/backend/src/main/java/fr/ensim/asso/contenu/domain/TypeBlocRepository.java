package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TypeBlocRepository extends JpaRepository<TypeBloc, TypeBloc.Cle> {

    /** La version courante d'un type : la plus haute non dépréciée. */
    @Query("""
           select t from TypeBloc t
            where t.type = :type and t.depreciee = false
            order by t.schemaVersion desc
           """)
    List<TypeBloc> versionsActives(@Param("type") String type);

    default Optional<TypeBloc> versionCourante(String type) {
        List<TypeBloc> l = versionsActives(type);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    @Query("select t from TypeBloc t where t.depreciee = false order by t.categorie, t.libelle")
    List<TypeBloc> catalogue();
}
