package fr.ensim.asso.portail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.ensim.asso.agenda.app.ServiceAgenda;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.media.app.ServiceMedia;
import fr.ensim.asso.partenariat.app.ServicePartenariat;
import fr.ensim.asso.shared.error.Erreurs;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ce que le portail public dit, et ce qu'il ne doit pas dire.
 *
 * <p>Deux promesses écrites dans le code et non tenues :
 * <ul>
 *   <li>« une association par ligne, AVEC SON MANDAT EN COURS » — l'annuaire
 *       listait aussi celles qui n'en ont aucune, et cliquer menait à un 404 ;</li>
 *   <li>« un mandat en préparation est invisible : le public ne doit rien en
 *       voir » — les deux 404 de la route d'archive portaient des messages
 *       DIFFÉRENTS, recopiés tels quels dans le corps de la réponse, sur une
 *       route ouverte sans jeton. Les comparer révélait le mandat.</li>
 * </ul>
 */
class PortailPublicTest {

    private static final UUID ASSO_DIRIGEE = UUID.randomUUID();
    private static final UUID ASSO_ORPHELINE = UUID.randomUUID();

    private AssociationRepository associations;
    private MandatRepository mandats;
    private ServicePortail portail;

    private Association dirigee;
    private Association orpheline;

    @BeforeEach
    void avant() {
        associations = mock(AssociationRepository.class);
        mandats = mock(MandatRepository.class);
        ServiceMedia medias = mock(ServiceMedia.class);
        when(medias.validiteUrlLecture()).thenReturn(Duration.ofMinutes(30));
        PortCache cache = mock(PortCache.class);
        when(cache.lire(any())).thenReturn(Optional.empty());

        portail = new ServicePortail(associations, mandats, mock(MembreBureauRepository.class),
                mock(PageRepository.class), mock(PageVersionRepository.class),
                mock(ThemeVersionRepository.class), mock(ServiceContenu.class), medias,
                mock(ServiceAgenda.class), mock(ServicePartenariat.class),
                mock(AnneeUniversitaireRepository.class), mock(PolitiqueAcces.class),
                cache, Duration.ofMinutes(5), new SimpleMeterRegistry(),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(OffsetDateTime.parse("2026-03-01T12:00:00Z").toInstant(),
                            ZoneOffset.UTC));

        dirigee = association(ASSO_DIRIGEE, "bde", "Bureau des Élèves");
        orpheline = association(ASSO_ORPHELINE, "club-neuf", "Club tout neuf");
        when(associations.findAll()).thenReturn(List.of(dirigee, orpheline));
    }

    private Association association(UUID id, String slug, String nom) {
        Association a = new Association(slug, nom, Association.TypeAssociation.CLUB);
        try {
            var champ = Association.class.getDeclaredField("id");
            champ.setAccessible(true);
            champ.set(a, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return a;
    }

    // ----------------------------------------------------------- l'annuaire

    @Test
    @DisplayName("l'annuaire ne liste que les associations qu'un bureau dirige")
    void annuaireSansLesOrphelines() {
        when(mandats.associationsEnFonction()).thenReturn(List.of(ASSO_DIRIGEE));

        assertThat(portail.annuaire()).extracting(PageRendue.AssociationVue::slug)
                .as("une association sans mandat n'a aucune page : la lister "
                  + "publie un lien qui ne mène nulle part")
                .containsExactly("bde");
    }

    @Test
    @DisplayName("et la ligne listée mène bien quelque part")
    void ligneListeeMeneQuelquePart() {
        when(mandats.associationsEnFonction()).thenReturn(List.of(ASSO_DIRIGEE));
        // Le témoin : c'est ce 404 que l'annuaire produisait sur « club-neuf ».
        when(mandats.mandatEnFonction(ASSO_ORPHELINE)).thenReturn(Optional.empty());
        when(associations.findBySlug("club-neuf")).thenReturn(Optional.of(orpheline));

        assertThat(portail.annuaire()).extracting(PageRendue.AssociationVue::slug)
                .doesNotContain("club-neuf");
        assertThatThrownBy(() -> portail.page("club-neuf", "accueil"))
                .isInstanceOf(Erreurs.Introuvable.class);
    }

    // ------------------------------------------ l'invisibilité d'un mandat

    @Test
    @DisplayName("une année inconnue et une année en préparation répondent PAREIL")
    void memeMessagePourLesDeuxAbsences() {
        when(associations.findBySlug("bde")).thenReturn(Optional.of(dirigee));

        // a) aucune ligne pour cette année
        when(mandats.findByAssociationIdAndAnneeCode(ASSO_DIRIGEE, "2030-2031"))
                .thenReturn(Optional.empty());
        String annéeInconnue = messageDe("2030-2031");

        // b) une ligne existe, mais le bureau entrant n'a rien publié
        Mandat enPreparation = Mandat.enPreparation(ASSO_DIRIGEE, "2030-2031",
                OffsetDateTime.parse("2030-09-01T00:00:00Z"),
                OffsetDateTime.parse("2031-08-31T00:00:00Z"));
        when(mandats.findByAssociationIdAndAnneeCode(ASSO_DIRIGEE, "2030-2031"))
                .thenReturn(Optional.of(enPreparation));
        String annéeEnPreparation = messageDe("2030-2031");

        // Le gestionnaire d'erreurs recopie ce message dans le corps du 404,
        // sur une route ouverte sans jeton. Deux messages différents, et
        // comparer les réponses révèle le mandat que cette branche existe
        // précisément pour cacher.
        assertThat(annéeEnPreparation)
                .as("« invisible » veut dire indiscernable, pas « pas affiché »")
                .isEqualTo(annéeInconnue);
    }

    private String messageDe(String annee) {
        try {
            portail.pageArchivee("bde", annee, "accueil");
            throw new AssertionError("un 404 était attendu");
        } catch (Erreurs.Introuvable e) {
            return e.getMessage();
        }
    }
}
