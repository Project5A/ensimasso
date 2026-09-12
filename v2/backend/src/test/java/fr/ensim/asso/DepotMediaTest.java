package fr.ensim.asso;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.AnneeUniversitaireRepository;
import fr.ensim.asso.gouvernance.domain.AssociationRepository;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.media.app.ServiceMedia;
import fr.ensim.asso.media.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * La confirmation d'un dépôt.
 *
 * <p>Ce que ces tests décrivent est exactement ce qui passait avant : le
 * {@code Content-Type} relu auprès de S3 est celui que le navigateur a écrit
 * dans son PUT signé. Le relire n'apprend rien sur le fichier — seulement sur
 * ce que le client a bien voulu déclarer.
 */
class DepotMediaTest {

    private static final UUID ASSO = UUID.randomUUID();
    private static final UUID DEPOSANT = UUID.randomUUID();
    private static final String CLE = "bde/2025-2026/9f1c.png";

    private MediaAssetRepository medias;
    private PortStockage stockage;
    private PolitiqueAcces politique;
    private ServiceMedia service;
    private MediaAsset media;

    private static final byte[] PNG = new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };

    @BeforeEach
    void avant() {
        medias = mock(MediaAssetRepository.class);
        stockage = mock(PortStockage.class);
        // Le mock d'une politique laisse tout passer : les tests ci-dessous
        // portent sur le CONTENU, et l'un d'eux vérifie que l'autorisation est
        // bien demandée — sans quoi ils décriraient un service sans contrôle
        // d'accès sans que rien ne le signale.
        politique = mock(PolitiqueAcces.class);

        service = new ServiceMedia(medias, stockage,
                mock(AssociationRepository.class), mock(AnneeUniversitaireRepository.class),
                politique, Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));

        media = new MediaAsset(ASSO, "2025-2026", CLE, "logo.png", "image/png", DEPOSANT);
        when(medias.findById(any())).thenReturn(Optional.of(media));
    }

    private void objetDepose(String contentTypeDeclare, byte[] contenu) {
        when(stockage.metadonnees(CLE))
                .thenReturn(Optional.of(new PortStockage.MetadonneesObjet(contenu.length, contentTypeDeclare)));
        when(stockage.lireDebut(anyString(), anyInt())).thenReturn(Optional.of(contenu));
    }

    @Test
    @DisplayName("un vrai PNG annoncé image/png est confirmé")
    void depotHonnete() {
        objetDepose("image/png", PNG);

        MediaAsset confirme = service.confirmerDepot(DEPOSANT, UUID.randomUUID());

        assertThat(confirme.getStatut()).isEqualTo(StatutMedia.DISPONIBLE);
        verify(stockage, never()).supprimer(anyString());
    }

    @ParameterizedTest(name = "refuse : {0}")
    @ValueSource(strings = {
            "<!DOCTYPE html><html><script>fetch('/api/contenu/pages')</script></html>",
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
            "<?php system($_GET['c']); ?>",
    })
    @DisplayName("un document exécutable déposé sous image/png est refusé ET effacé")
    void contenuHostileRefuse(String contenu) {
        objetDepose("image/png", contenu.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.confirmerDepot(DEPOSANT, UUID.randomUUID()))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("contenu refusé");

        assertThat(media.getStatut()).isEqualTo(StatutMedia.REJETE);
        // Laisser l'objet en place servirait une page hostile depuis l'origine
        // du stockage, que le rejet en base n'empêche pas.
        verify(stockage).supprimer(CLE);
    }

    @Test
    @DisplayName("le refus nomme le format trouvé, pour qu'on ne le contourne pas en renommant")
    void refusExplicite() {
        objetDepose("image/png", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.confirmerDepot(DEPOSANT, UUID.randomUUID()))
                .hasMessageContaining("SVG");
    }

    @Test
    @DisplayName("un fichier illisible est rejeté, pas accepté par défaut")
    void objetIllisible() {
        when(stockage.metadonnees(CLE))
                .thenReturn(Optional.of(new PortStockage.MetadonneesObjet(1024, "image/png")));
        when(stockage.lireDebut(anyString(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmerDepot(DEPOSANT, UUID.randomUUID()))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("vérifier le format");
        assertThat(media.getStatut()).isEqualTo(StatutMedia.REJETE);
    }

    @Test
    @DisplayName("le format réel doit correspondre au type annoncé, même entre images")
    void formatDiscordant() {
        byte[] jpeg = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };
        objetDepose("image/png", jpeg);

        assertThatThrownBy(() -> service.confirmerDepot(DEPOSANT, UUID.randomUUID()))
                .isInstanceOf(Erreurs.Conflit.class);
        assertThat(media.getStatut()).isEqualTo(StatutMedia.REJETE);
    }

    @Test
    @DisplayName("un paramètre de charset sur le type déclaré ne fait pas échouer un dépôt valide")
    void charsetTolere() {
        objetDepose("image/png; charset=binary", PNG);

        assertThat(service.confirmerDepot(DEPOSANT, UUID.randomUUID()).getStatut())
                .isEqualTo(StatutMedia.DISPONIBLE);
    }

    @Test
    @DisplayName("la confirmation exige toujours le droit de déposer sur l'association")
    void autorisationExigee() {
        objetDepose("image/png", PNG);
        service.confirmerDepot(DEPOSANT, UUID.randomUUID());
        verify(politique).exiger(DEPOSANT, Permission.MEDIA_DEPOSER, ASSO);
    }

    @Test
    @DisplayName("un fichier trop volumineux est refusé avant même d'être lu")
    void tropVolumineux() {
        when(stockage.metadonnees(CLE))
                .thenReturn(Optional.of(new PortStockage.MetadonneesObjet(20L * 1024 * 1024, "image/png")));

        assertThatThrownBy(() -> service.confirmerDepot(DEPOSANT, UUID.randomUUID()))
                .isInstanceOf(Erreurs.Conflit.class)
                .hasMessageContaining("volumineux");
        verify(stockage, never()).lireDebut(anyString(), anyInt());
    }
}
