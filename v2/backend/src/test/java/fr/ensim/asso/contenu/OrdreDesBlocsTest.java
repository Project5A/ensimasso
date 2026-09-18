package fr.ensim.asso.contenu;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.app.ValidationBloc;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.MandatRepository;
import fr.ensim.asso.media.domain.MediaAssetRepository;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La position d'un nouveau bloc, après qu'un trou a été creusé.
 *
 * <p>L'ordre se calculait avec {@code count()}. Les deux formules coïncident
 * tant que les ordres se suivent sans trou — et supprimer un bloc en creuse
 * un. Sur des blocs 0,1,2, retirer le 1 laisse 0 et 2 : {@code count()} vaut
 * alors 2, c'est-à-dire un ordre DÉJÀ PRIS. La contrainte
 * {@code bloc_ordre_unique} (V2, {@code UNIQUE (page_version_id, ordre)})
 * refuse l'insertion, et l'ajout sort en 500 — à chaque tentative, jusqu'à ce
 * que quelqu'un réordonne la page. Une seule suppression au milieu d'une page
 * suffisait à rendre l'éditeur inutilisable.
 *
 * <p>Le refus de la base a été rejoué contre un PostgreSQL 16 réellement
 * migré : avec des blocs 0 et 2, l'insertion à l'ordre 2 échoue sur
 * « duplicate key value violates unique constraint "bloc_ordre_unique" » et
 * celle à l'ordre 3 passe. Le dépôt simulé ci-dessous reproduit cette règle —
 * il ne l'invente pas.
 */
class OrdreDesBlocsTest {

    private static final UUID AUTEUR = UUID.randomUUID();
    private static final UUID MANDAT = UUID.randomUUID();

    private final List<Bloc> enBase = new ArrayList<>();
    private BlocRepository blocs;
    private ServiceContenu service;
    private PageVersion brouillon;

    @BeforeEach
    void avant() {
        blocs = mock(BlocRepository.class);
        PageRepository pages = mock(PageRepository.class);
        PageVersionRepository versions = mock(PageVersionRepository.class);

        brouillon = new PageVersion(UUID.randomUUID(), 1, AUTEUR);

        when(versions.findById(any())).thenReturn(Optional.of(brouillon));
        when(pages.findById(any())).thenReturn(Optional.of(new Page(MANDAT, "accueil", "Accueil", 0)));

        // Le dépôt simulé répond à partir d'un seul état — la liste `enBase`.
        // Aucune des trois réponses n'est écrite à la main : un test qui
        // stipulerait « dernierOrdre vaut 2 » se donnerait sa propre réponse et
        // ne dirait plus rien du calcul.
        when(blocs.dernierOrdre(any())).thenAnswer(i ->
                enBase.stream().mapToInt(Bloc::getOrdre).max().orElse(-1));
        when(blocs.existsByPageVersionIdAndOrdre(any(), anyInt())).thenAnswer(i ->
                enBase.stream().anyMatch(b -> b.getOrdre() == (int) i.getArgument(1)));
        when(blocs.save(any())).thenAnswer(i -> {
            Bloc nouveau = i.getArgument(0);
            // La contrainte bloc_ordre_unique, telle que la base l'applique.
            if (enBase.stream().anyMatch(b -> b.getOrdre() == nouveau.getOrdre())) {
                throw new IllegalStateException(
                        "bloc_ordre_unique : l'ordre " + nouveau.getOrdre() + " est déjà pris");
            }
            enBase.add(nouveau);
            return nouveau;
        });

        ValidationBloc validation = mock(ValidationBloc.class);
        when(validation.versionCourantePour(any())).thenReturn(1);

        service = new ServiceContenu(pages, versions, blocs,
                mock(MediaUsageRepository.class), mock(MediaAssetRepository.class),
                mock(MandatRepository.class), mock(TypeBlocRepository.class),
                validation, mock(PolitiqueAcces.class), new ObjectMapper());
    }

    private void blocsAuxOrdres(int... ordres) {
        for (int ordre : ordres) {
            enBase.add(new Bloc(brouillon.getId(), ordre, "RICH_TEXT", 1, "{\"doc\":{}}"));
        }
    }

    private Bloc ajouter(Integer ordreImpose) {
        return service.ajouterBloc(AUTEUR, brouillon.getId(), "RICH_TEXT", "{\"doc\":{}}", ordreImpose);
    }

    @Test
    @DisplayName("après une suppression au milieu, le bloc suivant ne retombe pas sur un ordre pris")
    void apresUneSuppressionAuMilieu() {
        blocsAuxOrdres(0, 2);           // 0,1,2 dont le 1 a été supprimé

        assertThat(ajouter(null).getOrdre())
                .as("count() aurait répondu 2, l'ordre du dernier bloc restant")
                .isEqualTo(3);

        // Et le trou ne se rebouche pas tout seul au coup suivant : chaque
        // ajout part du maximum, pas du nombre de blocs.
        assertThat(ajouter(null).getOrdre()).isEqualTo(4);
        assertThat(enBase).extracting(Bloc::getOrdre).containsExactly(0, 2, 3, 4);
    }

    @Test
    @DisplayName("le premier bloc d'une version vide prend l'ordre 0")
    void premierBlocDUneVersionVide() {
        assertThat(ajouter(null).getOrdre()).isEqualTo(0);
    }

    @Test
    @DisplayName("une position imposée et libre est respectée : elle rebouche le trou")
    void positionImposeeLibre() {
        blocsAuxOrdres(0, 2);

        assertThat(ajouter(1).getOrdre()).isEqualTo(1);
        assertThat(enBase).extracting(Bloc::getOrdre).containsExactly(0, 2, 1);
    }

    @Test
    @DisplayName("une position imposée et déjà prise est un 409, pas une violation de contrainte")
    void positionImposeeDejaPrise() {
        blocsAuxOrdres(0, 2);

        // L'appelant s'est trompé ; le lui dire vaut mieux que de laisser la
        // contrainte remonter en 500 avec un message de base de données.
        assertThatThrownBy(() -> ajouter(2))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("2")
                .hasMessageContaining("déjà occupée");

        assertThat(enBase).hasSize(2);
    }
}
