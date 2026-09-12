package fr.ensim.asso.media.app;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.media.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
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

    public ServiceMedia(MediaAssetRepository medias, PortStockage stockage,
                        AssociationRepository associations, AnneeUniversitaireRepository annees,
                        PolitiqueAcces politique, Clock horloge) {
        this.medias = medias;
        this.stockage = stockage;
        this.associations = associations;
        this.annees = annees;
        this.politique = politique;
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
     * <p>On ne fait pas confiance à ce que le client déclare avoir envoyé : la
     * taille et le type sont relus auprès du stockage. Un objet absent, trop
     * gros, ou d'un type hors liste blanche est rejeté.
     *
     * <p>Note honnête : la vérification des octets d'en-tête (« magic bytes »)
     * et l'analyse antivirale relèvent du worker média, qui n'est pas encore
     * écrit. Ce contrôle-ci est nécessaire, pas suffisant.
     */
    @Transactional
    public MediaAsset confirmerDepot(UUID demandeur, UUID mediaId) {
        MediaAsset media = media(mediaId);
        politique.exiger(demandeur, Permission.MEDIA_DEPOSER, media.getAssociationId());

        PortStockage.MetadonneesObjet meta = stockage.metadonnees(media.getCle())
                .orElseThrow(() -> new Erreurs.Conflit(
                        "aucun objet déposé pour cette clé : le dépôt a-t-il abouti ?"));

        if (meta.tailleOctets() > TAILLE_MAX_OCTETS) {
            media.rejeter();
            stockage.supprimer(media.getCle());
            throw new Erreurs.Conflit("fichier trop volumineux : "
                    + meta.tailleOctets() + " octets (maximum " + TAILLE_MAX_OCTETS + ")");
        }
        if (!TYPES_AUTORISES.containsKey(meta.contentType())) {
            media.rejeter();
            stockage.supprimer(media.getCle());
            throw new Erreurs.Conflit("type réel non autorisé : " + meta.contentType());
        }

        media.confirmer(meta.tailleOctets(), meta.contentType(), OffsetDateTime.now(horloge));
        return media;
    }

    // ------------------------------------------------------------- lecture

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
