package fr.ensim.asso.media.app;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.media.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Dépôt et lecture des médias.
 *
 * <p>Trois corrections de la v1 sont concentrées ici :
 * <ul>
 *   <li><strong>STOR-01</strong> — la base ne stocke que la clé ; l'URL est
 *       fabriquée à la lecture, donc elle n'expire jamais « définitivement ».</li>
 *   <li><strong>SEC-06</strong> — le dépôt exige une identité et un droit sur
 *       l'association. Dans la v1, {@code POST /api/posts/uploadImage} était
 *       en {@code permitAll} et acceptait n'importe quel fichier de 20 Mo.</li>
 *   <li>Le type est contraint par une liste blanche <em>et</em> signé dans
 *       l'URL de dépôt, puis revérifié auprès du stockage à la confirmation.</li>
 * </ul>
 */
@Service
public class ServiceMedia {

    /** Liste blanche. Tout le reste est refusé — y compris les SVG, qui
     *  peuvent embarquer du script et s'exécuteraient sur notre origine. */
    private static final Map<String, String> TYPES_AUTORISES = Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/webp", "webp",
            "image/avif", "avif",
            "image/gif",  "gif",
            "application/pdf", "pdf");

    private static final long TAILLE_MAX_OCTETS = 15L * 1024 * 1024;
    private static final Duration VALIDITE_DEPOT   = Duration.ofMinutes(10);
    private static final Duration VALIDITE_LECTURE = Duration.ofMinutes(30);

    private final MediaAssetRepository medias;
    private final PortStockage stockage;
    private final AssociationRepository associations;
    private final AnneeUniversitaireRepository annees;
    private final PolitiqueAcces politique;
    private final Clock horloge;

    /**
     * Combien de dépôts sont refusés, et pourquoi. La question derrière cette
     * mesure n'est pas technique : un site associatif qui se met soudain à
     * refuser des dizaines de fichiers HTML déguisés en images est en train
     * d'être sondé, et personne ne s'en apercevrait autrement.
     */
    private final MeterRegistry metriques;

    public ServiceMedia(MediaAssetRepository medias, PortStockage stockage,
                        AssociationRepository associations, AnneeUniversitaireRepository annees,
                        PolitiqueAcces politique, MeterRegistry metriques, Clock horloge) {
        this.medias = medias;
        this.stockage = stockage;
        this.associations = associations;
        this.annees = annees;
        this.politique = politique;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    // --------------------------------------------------------------- dépôt

    /**
     * Réserve une clé et rend une URL de dépôt direct vers le stockage.
     *
     * <p>Les octets ne transitent jamais par l'application : c'est ce qui
     * permettra au worker média de décoder un fichier hostile dans un
     * processus isolé, sans identifiants de base et sans entrée HTTP.
     */
    @Transactional
    public Depot preparerDepot(UUID demandeur, UUID associationId, String nomOriginal, String contentType) {
        politique.exiger(demandeur, Permission.MEDIA_DEPOSER, associationId);

        String extension = TYPES_AUTORISES.get(contentType);
        if (extension == null) {
            throw new Erreurs.RequeteInvalide(
                    "type de fichier non autorisé : " + contentType
                  + " (acceptés : " + String.join(", ", new TreeSet<>(TYPES_AUTORISES.keySet())) + ")");
        }

        Association asso = associations.findById(associationId)
                .orElseThrow(() -> new Erreurs.Introuvable("association", associationId));
        String annee = anneeCourante();

        // {slug}/{annee}/{uuid}.{ext} — range la médiathèque par mandat et rend
        // une politique de cycle de vie par année immédiate.
        String cle = asso.getSlug() + "/" + annee + "/" + UUID.randomUUID() + "." + extension;

        MediaAsset media = medias.save(
                new MediaAsset(associationId, annee, cle, nomOriginal, contentType, demandeur));

        PortStockage.UrlPresignee url =
                stockage.preparerDepot(cle, contentType, TAILLE_MAX_OCTETS, VALIDITE_DEPOT);

        return new Depot(media.getId(), cle, url, TAILLE_MAX_OCTETS);
    }

    /**
     * Confirme le dépôt en relisant l'objet réellement présent.
     *
     * <p>On ne fait pas confiance à ce que le client déclare avoir envoyé. La
     * taille et le type sont relus auprès du stockage — mais le
     * {@code Content-Type} que renvoie S3 est celui que le navigateur a écrit
     * dans son PUT signé : le relire, c'est relire le client. Les premiers
     * octets de l'objet sont donc lus et comparés à la signature du format
     * annoncé. Un document HTML porteur de script, déposé sous
     * {@code image/png}, franchissait le contrôle précédent sans difficulté.
     *
     * <p>Ce qui manque encore, et qui reste le travail du worker média : le
     * retrait des métadonnées EXIF, la génération des variantes, et l'analyse
     * antivirale. Tout cela suppose de <em>décoder</em> le fichier, ce qui ne
     * doit pas se produire dans ce processus.
     */
    @Transactional
    public MediaAsset confirmerDepot(UUID demandeur, UUID mediaId) {
        MediaAsset media = media(mediaId);
        politique.exiger(demandeur, Permission.MEDIA_DEPOSER, media.getAssociationId());

        PortStockage.MetadonneesObjet meta = stockage.metadonnees(media.getCle())
                .orElseThrow(() -> new Erreurs.Conflit(
                        "aucun objet déposé pour cette clé : le dépôt a-t-il abouti ?"));

        if (meta.tailleOctets() > TAILLE_MAX_OCTETS) {
            compter("refuse", "taille");
            rejeter(media, "fichier trop volumineux : "
                    + meta.tailleOctets() + " octets (maximum " + TAILLE_MAX_OCTETS + ")");
        }
        if (!TYPES_AUTORISES.containsKey(normaliser(meta.contentType()))) {
            compter("refuse", "type_declare");
            rejeter(media, "type déclaré non autorisé : " + meta.contentType());
        }

        verifierSignature(media, meta.contentType());

        media.confirmer(meta.tailleOctets(), normaliser(meta.contentType()), OffsetDateTime.now(horloge));
        compter("accepte", "aucun");
        return media;
    }

    private void compter(String resultat, String motif) {
        metriques.counter("ensimasso.media.depot", "resultat", resultat, "motif", motif).increment();
    }

    /**
     * Compare les octets réellement déposés au type annoncé.
     *
     * <p>Un objet illisible est rejeté plutôt qu'accepté par défaut : un dépôt
     * dont on ne peut pas lire le début est un dépôt qu'on ne peut pas vérifier.
     */
    private void verifierSignature(MediaAsset media, String contentTypeDeclare) {
        byte[] debut = stockage.lireDebut(media.getCle(), SignatureFichier.OCTETS_A_LIRE)
                .orElse(null);
        if (debut == null) {
            compter("refuse", "illisible");
            rejeter(media, "impossible de relire le fichier déposé pour en vérifier le format");
        }

        SignatureFichier.Type reel = SignatureFichier.identifier(debut);
        if (SignatureFichier.correspond(reel, contentTypeDeclare)) {
            return;
        }

        // Nommer le format trouvé : un refus muet se contourne en renommant le
        // fichier, un refus qui explique ne se contourne pas.
        String explication = switch (reel) {
            case SVG -> "ce fichier est un SVG, qui peut embarquer du script et "
                      + "s'exécuterait sur notre origine : il n'est jamais accepté";
            case HTML -> "ce fichier est un document HTML, pas une image";
            case SCRIPT -> "ce fichier est un script, pas une image";
            case INCONNU -> "le contenu ne correspond à aucun format accepté";
            default -> "le contenu est un fichier " + reel
                     + ", alors que le dépôt annonçait " + contentTypeDeclare;
        };
        // Le motif est l'information qui compte : « html » et « svg » disent
        // qu'on nous teste, « JPEG annoncé PNG » dit qu'un navigateur se trompe.
        compter("refuse", reel.name().toLowerCase(java.util.Locale.ROOT));
        rejeter(media, "contenu refusé : " + explication);
    }

    /** Rejette, efface l'objet, et échoue. Ne rend jamais la main. */
    private void rejeter(MediaAsset media, String raison) {
        media.rejeter();
        stockage.supprimer(media.getCle());
        throw new Erreurs.Conflit(raison);
    }

    /** « image/png; charset=utf-8 » et « image/png » désignent le même type. */
    private static String normaliser(String contentType) {
        if (contentType == null) {
            return "";
        }
        int pointVirgule = contentType.indexOf(';');
        return (pointVirgule >= 0 ? contentType.substring(0, pointVirgule) : contentType)
                .trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------- lecture

    /**
     * Combien de temps une URL de lecture reste valide.
     *
     * <p>Exposé parce que tout ce qui mémorise une page rendue doit expirer
     * AVANT elle. Une page gardée en cache plus longtemps que ses URL signées
     * afficherait des images mortes : ce serait STOR-01 réintroduit par la
     * porte de derrière, avec la même conséquence et une cause de plus.
     */
    public Duration validiteUrlLecture() {
        return VALIDITE_LECTURE;
    }

    /**
     * L'URL d'un média, fabriquée maintenant.
     *
     * <p>Le cœur de la correction STOR-01 : cette valeur n'est jamais écrite en
     * base. Elle expire, et la suivante est régénérée à la demande.
     */
    @Transactional(readOnly = true)
    public String urlDe(String cle) {
        MediaAsset media = medias.findByCle(cle)
                .orElseThrow(() -> new Erreurs.Introuvable("média", cle));
        if (!media.estDisponible()) {
            throw new Erreurs.Conflit("média non disponible (statut : " + media.getStatut() + ")");
        }
        return stockage.urlLecture(cle, VALIDITE_LECTURE);
    }

    /** Résolution en lot : une page de galerie ne doit pas faire N requêtes. */
    @Transactional(readOnly = true)
    public Map<String, String> urlsDe(Collection<String> cles) {
        Map<String, String> resultat = new LinkedHashMap<>();
        for (String cle : cles) {
            medias.findByCle(cle)
                    .filter(MediaAsset::estDisponible)
                    .ifPresent(m -> resultat.put(cle, stockage.urlLecture(cle, VALIDITE_LECTURE)));
        }
        return resultat;
    }

    /**
     * Les mêmes URL, mais seulement pour les médias que le demandeur peut voir.
     *
     * <p>La route du tableau de bord appelait la version sans identité : elle
     * signait une URL de lecture pour n'importe quelle clé, de n'importe quelle
     * association et de n'importe quelle année, pour tout compte authentifié.
     * Les objets sont pourtant privés et les URL présignées existent
     * précisément pour que l'accès reste contrôlé — le contrôle manquait juste
     * ici. La médiathèque, deux méthodes plus bas, l'exerçait bien : c'est la
     * résolution en lot qui y avait échappé.
     *
     * <p>Une clé refusée est simplement ABSENTE du résultat, comme une clé
     * inconnue : le contrat de la méthode est déjà « je rends ce que je peux
     * résoudre », et distinguer « interdit » de « inexistant » renseignerait un
     * appelant sur ce qu'il n'a pas le droit de voir.
     */
    @Transactional(readOnly = true)
    public Map<String, String> urlsDe(UUID demandeur, Collection<String> cles) {
        Map<String, String> resultat = new LinkedHashMap<>();
        for (String cle : cles) {
            medias.findByCle(cle)
                    .filter(MediaAsset::estDisponible)
                    .filter(m -> politique.peut(demandeur, Permission.MEDIA_DEPOSER, m.getAssociationId()))
                    .ifPresent(m -> resultat.put(cle, stockage.urlLecture(cle, VALIDITE_LECTURE)));
        }
        return resultat;
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> mediatheque(UUID demandeur, UUID associationId, String anneeCode) {
        politique.exiger(demandeur, Permission.MEDIA_DEPOSER, associationId);
        return medias.findByAssociationIdAndAnneeCodeOrderByCreeLeDesc(associationId, anneeCode);
    }

    // --------------------------------------------------------- suppression

    /**
     * Supprime un média — sauf s'il est utilisé par une version figée.
     *
     * <p>Sans ce garde-fou, supprimer une image casse des pages d'archive que
     * le système promet immuables, et personne ne peut répondre à « où cette
     * image est-elle utilisée ? ». La clé étrangère depuis {@code media_usage}
     * fait le même travail au niveau de la base.
     */
    @Transactional
    public void supprimer(UUID demandeur, UUID mediaId) {
        MediaAsset media = media(mediaId);
        politique.exiger(demandeur, Permission.MEDIA_SUPPRIMER, media.getAssociationId());

        long utilisations = medias.utilisationsFigees(media.getCle());
        if (utilisations > 0) {
            throw new Erreurs.Conflit("ce média est utilisé par " + utilisations
                    + " version(s) publiée(s) ou archivée(s) : le supprimer casserait ces pages. "
                    + "Retirez-le d'abord des brouillons concernés.");
        }

        stockage.supprimer(media.getCle());
        media.marquerSupprime();
    }

    @Transactional
    public MediaAsset decrire(UUID demandeur, UUID mediaId, String texteAlternatif) {
        MediaAsset media = media(mediaId);
        politique.exiger(demandeur, Permission.MEDIA_DEPOSER, media.getAssociationId());
        media.decrire(texteAlternatif);
        return media;
    }

    // ------------------------------------------------------------- interne

    private MediaAsset media(UUID id) {
        return medias.findById(id).orElseThrow(() -> new Erreurs.Introuvable("média", id));
    }

    private String anneeCourante() {
        return annees.anneeCouvrant(LocalDate.now(horloge))
                .map(AnneeUniversitaire::getCode)
                .orElseThrow(() -> new Erreurs.Conflit(
                        "aucune année universitaire déclarée pour la date du jour"));
    }

    /** Ce que le client reçoit pour téléverser directement vers le stockage. */
    public record Depot(UUID mediaId, String cle,
                        PortStockage.UrlPresignee url, long tailleMaxOctets) { }
}
