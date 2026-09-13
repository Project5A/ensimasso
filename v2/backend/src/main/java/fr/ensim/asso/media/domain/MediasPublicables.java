package fr.ensim.asso.media.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce qu'une association a le droit de faire servir par le portail public.
 *
 * <p>La règle vit ici, en un seul endroit, parce que trois modules la posent :
 * le contenu à la publication d'une page, l'agenda sur l'affiche d'un
 * évènement, le partenariat sur le logo d'un partenaire. Elle était d'abord
 * écrite dans le seul contenu ; les deux autres ne vérifiaient de leur clé que
 * qu'elle « ne ressemble pas à une URL ».
 *
 * <p>Trois questions, dont la clé étrangère de {@code media_usage} ne posait
 * que la première, et seulement pour les pages :
 * <ul>
 *   <li>la clé désigne-t-elle un média ? Sinon la page, l'évènement ou le logo
 *       s'afficherait cassé ;</li>
 *   <li>ce média appartient-il à CETTE association ? Le portail signe les URL
 *       d'une page publiée sans vérifier aucun droit — et n'a pas à le faire :
 *       une page publique est publique. C'est donc à l'écriture que
 *       l'appartenance se contrôle, sans quoi un bureau peut faire servir
 *       l'objet privé d'une autre association depuis sa propre page ;</li>
 *   <li>est-il DISPONIBLE ? Un média SUPPRIMÉ garde sa ligne — volontairement,
 *       pour que les archives restent explicables — donc la clé étrangère
 *       l'accepte encore, alors que l'objet n'est plus dans le stockage.</li>
 * </ul>
 *
 * <p>Une clé vide ou blanche n'est pas une clé : elle est ignorée plutôt que
 * refusée. La refuser produisait un message qui ne nommait rien — « &nbsp;
 * (aucun média ne porte cette clé) » — et rendait la page impubliable sans
 * dire quoi corriger, c'est-à-dire exactement le défaut que ce contrôle
 * existe pour supprimer.
 */
public final class MediasPublicables {

    private MediasPublicables() { }

    /**
     * Les clés refusées, chacune suivie de son motif. Liste vide : tout est
     * publiable.
     *
     * <p>Toutes les clés fautives sont rendues d'un coup. Corriger une page à
     * raison d'un aller-retour par image serait une punition, pas un message
     * d'erreur.
     */
    public static List<String> refus(MediaAssetRepository medias, UUID association,
                                     Collection<String> cles) {
        Set<String> demandees = new LinkedHashSet<>(cles);
        demandees.removeIf(c -> c == null || c.isBlank());
        if (demandees.isEmpty()) {
            return List.of();
        }

        Map<String, MediaAsset> connus = new HashMap<>();
        medias.findByCleIn(demandees).forEach(m -> connus.put(m.getCle(), m));

        List<String> refuses = new ArrayList<>();
        for (String cle : demandees) {
            MediaAsset media = connus.get(cle);
            if (media == null) {
                refuses.add(cle + " (aucun média ne porte cette clé)");
            } else if (!association.equals(media.getAssociationId())) {
                refuses.add(cle + " (ce média appartient à une autre association)");
            } else if (!media.estDisponible()) {
                refuses.add(cle + " (média " + media.getStatut() + ")");
            }
        }
        return refuses;
    }
}
