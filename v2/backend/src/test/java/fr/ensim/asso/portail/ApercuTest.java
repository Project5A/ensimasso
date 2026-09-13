package fr.ensim.asso.portail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.ensim.asso.agenda.app.ServiceAgenda;
import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.contenu.app.ServiceContenu;
import fr.ensim.asso.contenu.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.media.app.ServiceMedia;
import fr.ensim.asso.partenariat.app.ServicePartenariat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L'aperçu d'un brouillon, et la question à laquelle il répond mal.
 *
 * <p>« Il emprunte exactement le même chemin de rendu que la page publique »,
 * dit son javadoc, « c'est la seule façon qu'un aperçu dise la vérité ». Le
 * chemin était bien le même ; c'est le drapeau passé au départ qui ne l'était
 * pas. L'aperçu écrivait {@code statut == EN_FONCTION}, si bien qu'un mandat en
 * PRÉPARATION — le bureau entrant, celui qui compose le site de sa rentrée et
 * le seul à se servir de l'aperçu pour ça — voyait sa page rendue comme une
 * ARCHIVE.
 *
 * <p>La conséquence est visible à l'écran : le rendu d'archive ignore le filtre
 * du bloc agenda et le remplace par « TOUS », du plus récent au plus ancien.
 * Un bloc réglé sur « les 3 prochains » montrait les 3 DERNIERS.
 */
class ApercuTest {

    private static final OffsetDateTime MAINTENANT =
            OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID DEMANDEUR = UUID.randomUUID();

    private PageVersionRepository versions;
    private PageRepository pages;
    private MandatRepository mandats;
    private AssociationRepository associations;
    private ServiceContenu contenu;
    private ServiceAgenda agenda;
    private ServicePortail portail;

    private Association asso;
    private Page page;
    private PageVersion brouillon;

    @BeforeEach
    void avant() {
        associations = mock(AssociationRepository.class);
        mandats = mock(MandatRepository.class);
        pages = mock(PageRepository.class);
        versions = mock(PageVersionRepository.class);
        contenu = mock(ServiceContenu.class);
        agenda = mock(ServiceAgenda.class);
        ThemeVersionRepository themes = mock(ThemeVersionRepository.class);
        ServiceMedia medias = mock(ServiceMedia.class);
        PortCache cache = mock(PortCache.class);

        when(medias.validiteUrlLecture()).thenReturn(Duration.ofMinutes(30));
        when(medias.urlsDe(anyCollection())).thenReturn(Map.of());

        portail = new ServicePortail(associations, mandats, mock(MembreBureauRepository.class),
                pages, versions, themes, contenu, medias, agenda,
                mock(ServicePartenariat.class), mock(AnneeUniversitaireRepository.class),
                mock(PolitiqueAcces.class), cache, Duration.ofMinutes(5),
                new SimpleMeterRegistry(),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));

        asso = new Association("bde", "Bureau des Élèves", Association.TypeAssociation.BUREAU);
        page = new Page(UUID.randomUUID(), "accueil", "Accueil", 0);
        brouillon = new PageVersion(page.getId(), 2, DEMANDEUR);

        when(versions.findById(any())).thenReturn(Optional.of(brouillon));
        when(pages.findById(any())).thenReturn(Optional.of(page));
        when(associations.findById(any())).thenReturn(Optional.of(asso));
        when(pages.findByMandatIdOrderByOrdreMenuAsc(any())).thenReturn(List.of());
        when(themes.versionPubliee(any())).thenReturn(Optional.empty());
        when(cache.lire(any())).thenReturn(Optional.empty());

        // Un bloc agenda réglé sur « les 2 prochains ».
        when(contenu.blocsDe(any())).thenReturn(List.of(new Bloc(brouillon.getId(), 0,
                "EVENT_LIST", 1, "{\"filtre\":\"A_VENIR\",\"limite\":2}")));
    }

    private Evenement ev(String slug, OffsetDateTime debut) {
        return new Evenement(UUID.randomUUID(), slug, slug, debut);
    }

    /** Deux évènements déjà passés, trois à venir. */
    private void agendaDuMandat() {
        when(agenda.publicsDuMandat(any())).thenReturn(List.of(
                ev("rentree", MAINTENANT.minusMonths(4)),
                ev("galette", MAINTENANT.minusMonths(1)),
                ev("gala", MAINTENANT.plusDays(10)),
                ev("week-end", MAINTENANT.plusMonths(1)),
                ev("passation", MAINTENANT.plusMonths(3))));
    }

    private Mandat mandatAperçu(StatutMandat statut) {
        Mandat m = Mandat.enPreparation(asso.getId(), "2026-2027",
                MAINTENANT.minusMonths(6), MAINTENANT.plusMonths(6));
        if (statut != StatutMandat.PREPARATION) {
            m.investir(MAINTENANT.minusMonths(6));
        }
        if (statut == StatutMandat.CLOS) {
            m.clore(MAINTENANT.minusDays(1));
        }
        when(mandats.findById(any())).thenReturn(Optional.of(m));
        when(mandats.findByAssociationIdOrderByDebutLeDesc(any())).thenReturn(List.of(m));
        return m;
    }

    @Test
    @DisplayName("le bureau entrant voit sa page comme elle sera servie, pas comme une archive")
    void apercuDUnMandatEnPreparation() {
        mandatAperçu(StatutMandat.PREPARATION);
        agendaDuMandat();

        PageRendue rendue = portail.apercu(DEMANDEUR, brouillon.getId());

        // Le filtre réglé dans le bloc est respecté : les deux PROCHAINS, dans
        // l'ordre chronologique. Rendu comme une archive, on obtenait
        // « passation, week-end » — les deux derniers, à l'envers.
        assertThat(rendue.blocs().get(0).agenda())
                .extracting(PageRendue.EvenementVue::slug)
                .containsExactly("gala", "week-end");
        assertThat(rendue.mandat().estCourant())
                .as("un mandat en préparation n'est pas une archive : son bureau "
                  + "prépare le site qu'il servira")
                .isTrue();
    }

    @Test
    @DisplayName("un mandat en fonction : même chose, et c'était déjà le cas")
    void apercuDUnMandatEnFonction() {
        mandatAperçu(StatutMandat.EN_FONCTION);
        agendaDuMandat();

        PageRendue rendue = portail.apercu(DEMANDEUR, brouillon.getId());

        assertThat(rendue.blocs().get(0).agenda())
                .extracting(PageRendue.EvenementVue::slug)
                .containsExactly("gala", "week-end");
        assertThat(rendue.mandat().estCourant()).isTrue();
    }

    @Test
    @DisplayName("un mandat clos reste une archive, et montre son bilan")
    void apercuDUnMandatClos() {
        mandatAperçu(StatutMandat.CLOS);
        agendaDuMandat();

        PageRendue rendue = portail.apercu(DEMANDEUR, brouillon.getId());

        // Sur une archive, « à venir » n'a plus de sens : appliquer le filtre à
        // la lettre afficherait un agenda vide et donnerait à croire que ce
        // bureau n'a rien organisé. Tout, du plus récent au plus ancien.
        assertThat(rendue.blocs().get(0).agenda())
                .extracting(PageRendue.EvenementVue::slug)
                .containsExactly("passation", "week-end");
        assertThat(rendue.mandat().estCourant()).isFalse();
    }

    @Test
    @DisplayName("la règle « vivante ou archive » ne dépend que de la clôture")
    void regleUnique() {
        assertThat(ServicePortail.estPageCourante(mandatAperçu(StatutMandat.PREPARATION))).isTrue();
        assertThat(ServicePortail.estPageCourante(mandatAperçu(StatutMandat.EN_FONCTION))).isTrue();
        assertThat(ServicePortail.estPageCourante(mandatAperçu(StatutMandat.CLOS))).isFalse();
    }
}
