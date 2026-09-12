package fr.ensim.asso.portail;

import fr.ensim.asso.agenda.domain.Evenement;
import fr.ensim.asso.partenariat.domain.Partenaire;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce qu'un bloc retient parmi les données de son mandat.
 *
 * <p>Fonctions pures, séparées du service : c'est la règle temporelle du
 * portail, celle qui se trompe silencieusement si on ne la teste pas, et la
 * sortir d'un service transactionnel permet de la vérifier sans base.
 */
final class SelectionBlocs {

    /** Bornes du JSON Schema d'EVENT_LIST, répétées ici : un payload trafiqué
     *  ne doit pas pouvoir faire rendre dix mille évènements. */
    private static final long LIMITE_DEFAUT = 6;
    private static final long LIMITE_MAX = 24;

    private SelectionBlocs() { }

    /**
     * Les évènements retenus par un bloc agenda.
     *
     * <p>Le filtre « à venir » n'a de sens que sur une page en cours. Sur une
     * archive, tout est passé par construction : appliquer le filtre à la
     * lettre afficherait un agenda vide sur toutes les pages d'archive, ce qui
     * donnerait à croire que ce bureau n'a rien organisé. La page d'un mandat
     * terminé montre donc son agenda complet, du plus récent au plus ancien —
     * c'est-à-dire son bilan.
     */
    static List<Evenement> agenda(Map<String, Object> payload, List<Evenement> tous,
                                  boolean estCourant, OffsetDateTime maintenant) {
        String filtre = estCourant ? texte(payload.get("filtre"), "A_VENIR") : "TOUS";
        List<Evenement> retenus = switch (filtre) {
            case "PASSES" -> tous.stream().filter(e -> e.estPasse(maintenant))
                    .sorted(Comparator.comparing(Evenement::getDebutLe).reversed()).toList();
            case "TOUS" -> tous.stream()
                    .sorted(Comparator.comparing(Evenement::getDebutLe).reversed()).toList();
            // « À venir » garde l'ordre chronologique croissant : le prochain
            // évènement en premier, ce qui est la seule lecture utile.
            default -> tous.stream().filter(e -> !e.estPasse(maintenant))
                    .sorted(Comparator.comparing(Evenement::getDebutLe)).toList();
        };
        return retenus.stream().limit(limite(payload.get("limite"))).toList();
    }

    /** Les partenaires retenus par un bloc partenaires : tous, ou les niveaux demandés. */
    static List<Partenaire> partenaires(Map<String, Object> payload, List<Partenaire> tous) {
        if (!(payload.get("niveaux") instanceof List<?> demandes) || demandes.isEmpty()) {
            return tous;
        }
        Set<String> vises = demandes.stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .collect(Collectors.toSet());
        return tous.stream().filter(p -> vises.contains(p.getNiveau().name())).toList();
    }

    private static String texte(Object valeur, String defaut) {
        return valeur instanceof String s && !s.isBlank() ? s : defaut;
    }

    private static long limite(Object valeur) {
        if (valeur instanceof Number n) {
            long l = n.longValue();
            if (l >= 1 && l <= LIMITE_MAX) {
                return l;
            }
        }
        return LIMITE_DEFAUT;
    }
}
