package fr.ensim.asso;

import fr.ensim.asso.adhesion.app.ServiceAdhesion;
import fr.ensim.asso.adhesion.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.AnneeUniversitaire;
import fr.ensim.asso.gouvernance.domain.AnneeUniversitaireRepository;
import fr.ensim.asso.gouvernance.domain.MandatRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ce qui arrive quand le calendrier universitaire a un trou.
 *
 * <p>{@code estAdherent} répondait « non » — à tout le monde, et sans rien
 * dire. Ce n'est pas une réponse : c'est une panne de configuration qui a
 * l'apparence d'une réponse. Le 1er septembre, si personne n'a inscrit l'année
 * suivante, tous les adhérents de toutes les associations cessent d'en être et
 * rien ne distingue cela d'un non-adhérent ordinaire.
 */
class CalendrierAdhesionTest {

    private final AdhesionRepository adhesions = mock(AdhesionRepository.class);

    private record Montage(ServiceAdhesion service, SimpleMeterRegistry metriques) { }

    private Montage monter(Optional<AnneeUniversitaire> anneeCouvrante) {
        var annees = mock(AnneeUniversitaireRepository.class);
        when(annees.anneeCouvrant(any())).thenReturn(anneeCouvrante);
        var metriques = new SimpleMeterRegistry();
        var service = new ServiceAdhesion(
                mock(CampagneAdhesionRepository.class),
                mock(TarifAdhesionRepository.class),
                adhesions,
                mock(MandatRepository.class),
                annees,
                mock(PolitiqueAcces.class),
                metriques,
                Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC));
        return new Montage(service, metriques);
    }

    @Test
    @DisplayName("sans année couvrant aujourd'hui, le trou est COMPTÉ, pas seulement subi")
    void trouDansLeCalendrierCompte() {
        var m = monter(Optional.empty());

        assertThat(m.service().estAdherent(UUID.randomUUID(), UUID.randomUUID()))
                .as("on ne peut pas répondre « oui » sans calendrier, et lever "
                  + "ferait tomber le portail public pour une ligne manquante")
                .isFalse();

        assertThat(m.metriques().counter("ensimasso.adhesion.calendrier_absent").count())
                .as("le compteur est le seul moyen de distinguer « pas adhérent » "
                  + "de « la plateforme ne sait pas quelle année on est »")
                .isEqualTo(1.0);

        // Et la base n'a même pas été interrogée : la question n'a pas de sens.
        verify(adhesions, never()).estAdherent(any(), any(), any());
    }

    @Test
    @DisplayName("avec une année couvrant aujourd'hui, rien n'est compté et la base décide")
    void calendrierComplet() {
        var annee = new AnneeUniversitaire("2026-2027",
                LocalDate.parse("2026-09-01"), LocalDate.parse("2027-08-31"));
        var m = monter(Optional.of(annee));
        UUID personne = UUID.randomUUID();
        UUID asso = UUID.randomUUID();
        when(adhesions.estAdherent(personne, asso, "2026-2027")).thenReturn(true);

        assertThat(m.service().estAdherent(personne, asso)).isTrue();
        assertThat(m.metriques().counter("ensimasso.adhesion.calendrier_absent").count())
                .isZero();
    }
}
