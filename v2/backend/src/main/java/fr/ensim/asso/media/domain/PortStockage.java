package fr.ensim.asso.media.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Le stockage objet, vu par le domaine.
 *
 * <p>C'est le seul endroit du système qui sait qu'un stockage S3 existe.
 * Ce n'est pas de la cérémonie : dans la v1, {@code BlobStorageService} jouait
 * déjà ce rôle pour Azure et c'est précisément pour cela que la migration du
 * stockage a été évaluée « effort faible » dans l'audit. On conserve la bonne
 * frontière, on corrige l'usage qui en était fait.
 */
public interface PortStockage {

    /**
     * Prépare un dépôt direct navigateur → stockage.
     *
     * <p>L'application ne voit jamais passer les octets : c'est ce qui permet
     * au worker média de traiter un fichier hostile dans un processus isolé,
     * sans que le constructeur de pages ne soit jamais exposé à un décodeur
     * d'image.
     */
    UrlPresignee preparerDepot(String cle, String contentType, long tailleMaxOctets, Duration validite);

    /**
     * Fabrique une URL de lecture <em>maintenant</em>.
     *
     * <p>Jamais persistée. C'est toute la correction de STOR-01.
     */
    String urlLecture(String cle, Duration validite);

    Optional<MetadonneesObjet> metadonnees(String cle);

    /**
     * Lit les premiers octets d'un objet, par une requête de plage.
     *
     * <p>Sert à vérifier la signature réelle d'un fichier. L'objet n'est pas
     * téléchargé : on lit une poignée d'octets et on les compare à des
     * constantes. Aucun décodeur n'est exposé — décoder un fichier hostile
     * reste le travail du worker média, dans un processus isolé.
     */
    Optional<byte[]> lireDebut(String cle, int octets);

    void supprimer(String cle);

    /** Une URL de dépôt et les en-têtes que le client doit rejouer tels quels. */
    record UrlPresignee(String url, String methode, java.util.Map<String, String> enTetes,
                        java.time.OffsetDateTime expireLe) { }

    record MetadonneesObjet(long tailleOctets, String contentType) { }
}
