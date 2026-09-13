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
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Le coût du rendu d'une page en médias.
 *
 * <p>{@code rendreBloc} appelait {@code medias.urlsDe} <em>par bloc</em>, et
 * {@code urlsDe} faisait alors une requête <em>par clé</em>. Une page de dix
 * blocs illustrés partait donc en plusieurs dizaines d'allers-retours pour
 * afficher ses images — sur le chemin de lecture public, celui que tout le
 * monde emprunte, et derrière un cache dont chaque défaut paie la facture
 * entière.
 *
 * <p>Rien ne le signalait : le rendu était correct, seulement cher. Ce test
 * compte les appels, parce qu'un test qui ne regarde que la page rendue
 * laisserait la boucle revenir sans rien dire.
 */
class RenduEnLotTest {

    private static final UUID MANDAT = UUID.randomUUID();
    private static final OffsetDateTime MAINTENANT =
            OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private MembreBureauRepository membres;
    private PageRepository pages;
    private PageVersionRepository versions;
    private ServiceContenu contenu;
    private ServiceMedia medias;
    private ServiceAgenda agenda;
    private ServicePortail portail;

    private Association asso;
    private Mandat mandat;
    private Page page;
    private PageVersion publiee;

    @BeforeEach
    void avant() {
        AssociationRepository associations = mock(AssociationRepository.class);
        MandatRepository mandats = mock(MandatRepository.class);
        membres = mock(MembreBureauRepository.class);
        pages = mock(PageRepository.class);
        versions = mock(PageVersionRepository.class);
        ThemeVersionRepository themes = mock(ThemeVersionRepository.class);
        contenu = mock(ServiceContenu.class);
        medias = mock(ServiceMedia.class);
        agenda = mock(ServiceAgenda.class);
        ServicePartenariat partenariats = mock(ServicePartenariat.class);
        PortCache cache = mock(PortCache.class);

        // Lu DANS le constructeur, pour plafonner la durée du cache.
        when(medias.validiteUrlLecture()).thenReturn(Duration.ofMinutes(30));

        portail = new ServicePortail(associations, mandats, membres, pages, versions, themes,
                contenu, medias, agenda, partenariats,
                mock(AnneeUniversitaireRepository.class), mock(PolitiqueAcces.class),
                cache, Duration.ofMinutes(5), new SimpleMeterRegistry(),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));

        asso = new Association("bde", "Bureau des Élèves", Association.TypeAssociation.BUREAU);
        mandat = Mandat.enPreparation(UUID.randomUUID(), "2025-2026",
                MAINTENANT.minusMonths(6), MAINTENANT.plusMonths(6));
        mandat.investir(MAINTENANT.minusMonths(6));
        page = new Page(MANDAT, "accueil", "Accueil", 0);
        publiee = new PageVersion(UUID.randomUUID(), 4, UUID.randomUUID());
        publiee.publier(UUID.randomUUID(), MAINTENANT.minusDays(1));

        when(associations.findBySlug("bde")).thenReturn(Optional.of(asso));
        when(mandats.mandatEnFonction(any())).thenReturn(Optional.of(mandat));
        when(mandats.findByAssociationIdOrderByDebutLeDesc(any())).thenReturn(List.of(mandat));
        when(pages.findByMandatIdAndSlug(any(), eq("accueil"))).thenReturn(Optional.of(page));
        when(pages.findByMandatIdOrderByOrdreMenuAsc(any())).thenReturn(List.of());
        when(versions.versionPubliee(any())).thenReturn(Optional.of(publiee));
        when(themes.versionPubliee(any())).thenReturn(Optional.empty());
        when(membres.membresActifs(any())).thenReturn(List.of());
        when(cache.lire(any())).thenReturn(Optional.empty());

        // Le lot rend ce qu'on lui demande : ce test porte sur QUI appelle et
        // COMBIEN de fois, pas sur la résolution elle-même — celle-ci est
        // vérifiée par ResolutionEnLotTest.
        when(medias.urlsDe(anyCollection())).thenAnswer(i -> {
            Collection<String> cles = i.getArgument(0);
            Map<String, String> urls = new LinkedHashMap<>();
            cles.forEach(c -> urls.put(c, "https://stockage.invalide/" + c));
            return urls;
        });
    }

    private Bloc illustre(int ordre, String cle) {
        return new Bloc(publiee.getId(), ordre, "HERO", 1,
                "{\"titre\":\"T" + ordre + "\",\"mediaKey\":\"" + cle + "\"}");
    }

    @SuppressWarnings("unchecked")
    private Collection<String> clesDemandees() {
        ArgumentCaptor<Collection<String>> capture = ArgumentCaptor.forClass(Collection.class);
        verify(medias).urlsDe(capture.capture());
        return capture.getValue();
    }

    @Test
    @DisplayName("six blocs illustrés : UN seul lot de médias pour toute la page")
    void unSeulLotParPage() {
        when(contenu.blocsDe(any())).thenReturn(List.of(
                illustre(0, "k0"), illustre(1, "k1"), illustre(2, "k2"),
                illustre(3, "k3"), illustre(4, "k4"), illustre(5, "k5")));

        PageRendue rendue = portail.page("bde", "accueil");

        assertThat(rendue.blocs()).hasSize(6);
        // La vérification : un appel, pas six.
        verify(medias, times(1)).urlsDe(anyCollection());
        assertThat(clesDemandees()).containsExactly("k0", "k1", "k2", "k3", "k4", "k5");
    }

    @Test
    @DisplayName("chaque bloc ne porte que SES URL, pas celles de toute la page")
    void chaqueBlocGardeLesSiennes() {
        when(contenu.blocsDe(any())).thenReturn(List.of(
                illustre(0, "k0"), illustre(1, "k1"), illustre(2, "k2")));

        PageRendue rendue = portail.page("bde", "accueil");

        // Résoudre en lot ne doit pas recopier tout le lot dans chaque bloc :
        // la page mémorisée grossirait du carré du nombre de blocs.
        assertThat(rendue.blocs()).extracting(PageRendue.BlocRendu::urlsMedias)
                .containsExactly(
                        Map.of("k0", "https://stockage.invalide/k0"),
                        Map.of("k1", "https://stockage.invalide/k1"),
                        Map.of("k2", "https://stockage.invalide/k2"));
    }

    @Test
    @DisplayName("l'affiche d'un évènement entre dans le même lot que les blocs")
    void lesAffichesSontDansLeLot() {
        Evenement gala = new Evenement(MANDAT, "gala", "Gala", MAINTENANT.plusMonths(1));
        gala.decrire("Gala", null, null, "Palais", MAINTENANT.plusMonths(1), null,
                "affiche-gala.jpg", null, false);
        when(agenda.publicsDuMandat(any())).thenReturn(List.of(gala));
        when(contenu.blocsDe(any())).thenReturn(List.of(
                illustre(0, "banniere"),
                new Bloc(publiee.getId(), 1, "EVENT_LIST", 1, "{\"filtre\":\"A_VENIR\"}")));

        PageRendue rendue = portail.page("bde", "accueil");

        verify(medias, times(1)).urlsDe(anyCollection());
        assertThat(clesDemandees()).containsExactly("banniere", "affiche-gala.jpg");
        // Et l'affiche arrive bien jusqu'à la vue : sans cela, le lot serait
        // complet mais l'évènement s'afficherait sans image.
        assertThat(rendue.blocs().get(1).agenda().get(0).afficheUrl())
                .isEqualTo("https://stockage.invalide/affiche-gala.jpg");
    }

    @Test
    @DisplayName("une page sans image ne demande aucun média")
    void pageSansImage() {
        when(contenu.blocsDe(any())).thenReturn(List.of(
                new Bloc(publiee.getId(), 0, "TEXT", 1, "{\"corps\":\"Bonjour\"}")));

        portail.page("bde", "accueil");

        // Une page de texte ne doit pas payer un aller-retour pour rien.
        verify(medias, never()).urlsDe(anyCollection());
    }
}
