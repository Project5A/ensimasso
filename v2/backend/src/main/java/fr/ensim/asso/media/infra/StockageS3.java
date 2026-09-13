package fr.ensim.asso.media.infra;

import fr.ensim.asso.media.domain.PortStockage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptateur S3, utilisé avec MinIO en auto-hébergement.
 *
 * <p>{@code pathStyleAccessEnabled} est indispensable avec MinIO, qui n'expose
 * pas de sous-domaines par bucket comme le fait AWS.
 *
 * <p>Les objets restent privés. Les URL de lecture sont présignées et de courte
 * durée, fabriquées à chaque lecture — jamais stockées.
 */
@Component
@ConditionalOnProperty(name = "ensimasso.stockage.type", havingValue = "s3", matchIfMissing = true)
public class StockageS3 implements PortStockage, AutoCloseable {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public StockageS3(@Value("${ensimasso.stockage.endpoint}") String endpoint,
                      @Value("${ensimasso.stockage.acces}") String acces,
                      @Value("${ensimasso.stockage.secret}") String secret,
                      @Value("${ensimasso.stockage.region:us-east-1}") String region,
                      @Value("${ensimasso.stockage.bucket}") String bucket) {
        exigerConfiguration(endpoint, acces, secret, bucket);
        this.bucket = bucket;
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(acces, secret));
        var config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(config)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(config)
                .build();
    }

    /**
     * Refuse de démarrer sur une configuration incomplète, en nommant ce qui
     * manque.
     *
     * <p>Sans cette garde, un secret absent remontait un
     * {@code NullPointerException: Secret access key cannot be blank} jeté au
     * fond du SDK AWS, au milieu de six « Error creating bean ». Le message ne
     * disait ni quelle variable manquait, ni où la définir : le démarrage
     * échouait et personne ne savait pourquoi. C'est ainsi que l'intégration
     * continue est restée rouge quatorze fois de suite.
     *
     * <p>Échouer au démarrage reste le bon choix — un stockage mal configuré
     * ne se découvre pas au premier dépôt d'affiche. Seul le message change.
     */
    private static void exigerConfiguration(String endpoint, String acces, String secret, String bucket) {
        var manquants = new java.util.ArrayList<String>();
        if (endpoint == null || endpoint.isBlank()) manquants.add("MINIO_ENDPOINT");
        if (acces == null || acces.isBlank()) manquants.add("MINIO_USER");
        if (secret == null || secret.isBlank()) manquants.add("MINIO_PASSWORD");
        if (bucket == null || bucket.isBlank()) manquants.add("MINIO_BUCKET_ORIGINAUX");
        if (!manquants.isEmpty()) {
            throw new IllegalStateException(
                    "stockage objet non configuré : " + String.join(", ", manquants)
                    + (manquants.size() > 1
                            ? " sont absentes. Renseignez-les"
                            : " est absente. Renseignez-la")
                    + " dans .env (voir .env.example) puis relancez ;"
                    + " « make up » démarre le MinIO correspondant.");
        }
    }

    @Override
    public UrlPresignee preparerDepot(String cle, String contentType, long tailleMaxOctets, Duration validite) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(cle)
                .contentType(contentType)
                // Signer le content-type l'impose : le client ne peut pas
                // déposer un exécutable en annonçant une image.
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(validite)
                .putObjectRequest(put)
                .build());

        return new UrlPresignee(
                presigned.url().toString(),
                "PUT",
                Map.of("Content-Type", contentType),
                OffsetDateTime.now().plus(validite));
    }

    @Override
    public String urlLecture(String cle, Duration validite) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validite)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(cle).build())
                .build());
        return presigned.url().toString();
    }

    @Override
    public Optional<MetadonneesObjet> metadonnees(String cle) {
        try {
            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(cle).build());
            return Optional.of(new MetadonneesObjet(head.contentLength(), head.contentType()));
        } catch (S3Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Requête par plage : on lit les premiers octets, pas l'objet.
     *
     * <p>Un fichier de 15 Mo coûte ici la même chose qu'un fichier de 500
     * octets — la vérification de signature reste donc possible sur chaque
     * dépôt, sans condition ni exception.
     */
    @Override
    public Optional<byte[]> lireDebut(String cle, int octets) {
        try {
            var reponse = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(cle)
                    .range("bytes=0-" + (octets - 1))
                    .build());
            return Optional.of(reponse.asByteArray());
        } catch (S3Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void supprimer(String cle) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(cle).build());
    }

    @Override
    public void close() {
        client.close();
        presigner.close();
    }
}
