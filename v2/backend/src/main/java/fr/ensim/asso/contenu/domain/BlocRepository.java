package fr.ensim.asso.contenu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface BlocRepository extends JpaRepository<Bloc, UUID> {
    List<Bloc> findByPageVersionIdOrderByOrdreAsc(UUID pageVersionId);

    /**
     * Le plus grand ordre utilisé, ou -1 si la version est vide.
     *
     * <p>L'ordre d'un nouveau bloc se calculait avec {@code count()}. Les deux
     * coïncident tant qu'aucun trou n'existe — et une suppression en crée un :
     * sur des blocs 0,1,2, retirer le 1 laisse 0 et 2, {@code count()} vaut 2,
     * et le bloc suivant naît à l'ordre 2, déjà pris. La contrainte
     * {@code bloc_ordre_unique} le refuse, et l'ajout sort en 500. Une seule
     * suppression au milieu d'une page suffit à rendre l'éditeur inutilisable
     * jusqu'au prochain réordonnancement.
     */
    @Query("select coalesce(max(b.ordre), -1) from Bloc b where b.pageVersionId = :version")
    int dernierOrdre(@Param("version") UUID pageVersionId);

    boolean existsByPageVersionIdAndOrdre(UUID pageVersionId, int ordre);
}
