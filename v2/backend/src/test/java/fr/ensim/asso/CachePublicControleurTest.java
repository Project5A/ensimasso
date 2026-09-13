package fr.ensim.asso;

import fr.ensim.asso.portail.PageRendue;
import fr.ensim.asso.portail.PortailController;
import fr.ensim.asso.portail.ServicePortail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le {@code Cache-Control} des réponses publiques, borné par la validité des
 * URL signées qu'elles transportent.
 */
class CachePublicControleurTest {

    @Test
    @DisplayName("aucune réponse publique n'est mémorisable plus longtemps que ses URL signées")
    void cacheControlBornéParLaValiditeDesUrls() {
        ServicePortail service = mock(ServicePortail.class);
        // Ce que le service s'impose à lui-même : la moitié des 30 minutes de
        // validité des URL de médias.
        when(service.dureeCachePublic()).thenReturn(Duration.ofMinutes(15));
        when(service.pageArchivee(anyString(), anyString(), anyString()))
                .thenReturn(mock(PageRendue.class));
        when(service.annuaire()).thenReturn(List.of());

        PortailController controleur = new PortailController(service);

        // L'archive demandait une heure, « parce qu'une archive ne change
        // plus ». Ce n'est pas la page qui expire, ce sont ses URL signées :
        // le navigateur gardait une heure une page dont les images meurent au
        // bout de trente minutes.
        ResponseEntity<PageRendue> archive = controleur.archive("bde", "2024-2025", "accueil");
        assertThat(archive.getHeaders().getCacheControl())
                .as("une heure de Cache-Control sur des URL valides trente minutes")
                .contains("max-age=900");

        // Ce qui est déjà plus court que le plafond n'est pas allongé.
        assertThat(controleur.annuaire().getHeaders().getCacheControl())
                .contains("max-age=300");
    }

}
