package fr.ensim.asso.media.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L'adaptateur de stockage, et la distinction dont dépend la survie des dépôts.
 *
 * <p>« L'objet n'existe pas » et « je n'ai pas pu regarder » remontaient tous
 * deux un {@code Optional} vide. En face, le service en conclut « dépôt
 * illisible » et SUPPRIME l'objet : une panne passagère de MinIO détruisait
 * définitivement le fichier qu'on venait de déposer.
 *
 * <p>Cet adaptateur n'avait aucun test — tout le reste mocke le port. Le
 * comportement fautif pouvait donc revenir sans que rien ne l'annonce.
 */
class StockageS3Test {

    private final S3Client client = mock(S3Client.class);
    private final StockageS3 stockage =
            new StockageS3(client, mock(S3Presigner.class), "media-originaux");

    private static S3Exception panne(int code, String message) {
        return (S3Exception) S3Exception.builder()
                .statusCode(code)
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage(message).build())
                .message(message)
                .build();
    }

    // ------------------------------------------------------------ lireDebut

    @Test
    @DisplayName("un objet absent est un Optional vide : le dépôt n'a pas abouti")
    void lireDebutObjetAbsent() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("pas là").build());

        assertThat(stockage.lireDebut("cle", 64)).isEmpty();
    }

    @Test
    @DisplayName("une panne du stockage REMONTE : elle ne se déguise pas en objet illisible")
    void lireDebutPanne() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(panne(500, "Internal Error"));

        assertThatThrownBy(() -> stockage.lireDebut("cle", 64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponible")
                .hasMessageContaining("cle");
    }

    @Test
    @DisplayName("un délai dépassé remonte lui aussi, plutôt que d'effacer un fichier valide")
    void lireDebutTimeout() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(panne(503, "Slow Down"));

        assertThatThrownBy(() -> stockage.lireDebut("cle", 64))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------- metadonnees

    @Test
    @DisplayName("métadonnées d'un objet absent : Optional vide")
    void metadonneesObjetAbsent() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("pas là").build());

        assertThat(stockage.metadonnees("cle")).isEmpty();
    }

    @Test
    @DisplayName("métadonnées pendant une panne : l'erreur remonte")
    void metadonneesPanne() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(panne(500, "Internal Error"));

        assertThatThrownBy(() -> stockage.metadonnees("cle"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("un objet présent rend bien sa taille et son type")
    void metadonneesObjetPresent() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(4096L).contentType("image/png").build());

        assertThat(stockage.metadonnees("cle"))
                .hasValueSatisfying(m -> {
                    assertThat(m.tailleOctets()).isEqualTo(4096L);
                    assertThat(m.contentType()).isEqualTo("image/png");
                });
    }
}
