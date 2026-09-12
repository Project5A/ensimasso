package fr.ensim.asso.agenda.app;

import fr.ensim.asso.agenda.domain.*;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * L'agenda d'un mandat.
 *
 * <p>La lecture publique ne passe par aucune autorisation — c'est un agenda
 * public. L'écriture passe toujours par {@code PolitiqueAcces} et porte sur un
 * mandat nommément désigné : un bureau clos ne peut plus rien écrire, donc
 * l'agenda d'une archive est figé comme le reste de sa page.
 */
@Service
public class ServiceAgenda {

    /** Un slug d'URL, pas un titre libre : il finira dans une adresse. */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    /** Liste blanche d'URL, doublée de la contrainte CHECK en base. */
    private static final Pattern LIEN = Pattern.compile("^https?://\\S+$");

    private final EvenementRepository evenements;
    private final PolitiqueAcces politique;
    private final Clock horloge;

    public ServiceAgenda(EvenementRepository evenements, PolitiqueAcces politique, Clock horloge) {
        this.evenements = evenements;
        this.politique = politique;
        this.horloge = horloge;
    }

    /** Ce que le public voit : tout sauf les brouillons, annulés compris. */
    @Transactional(readOnly = true)
    public List<Evenement> publicsDuMandat(UUID mandatId) {
        return evenements.findByMandatIdAndStatutInOrderByDebutLeAsc(
                mandatId, EnumSet.of(StatutEvenement.PUBLIE, StatutEvenement.ANNULE));
    }

    /** Ce que le bureau voit dans son tableau de bord : ses brouillons aussi. */
    @Transactional(readOnly = true)
    public List<Evenement> tousDuMandat(UUID demandeur, UUID mandatId) {
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, mandatId);
        return evenements.findByMandatIdOrderByDebutLeAsc(mandatId);
    }

    @Transactional
    public Evenement creer(UUID demandeur, UUID mandatId, String slug, Description d) {
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, mandatId);
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new Erreurs.RequeteInvalide("slug d'évènement invalide : " + slug);
        }
        if (evenements.existsByMandatIdAndSlug(mandatId, slug)) {
            throw new Erreurs.Conflit("un évènement « " + slug + " » existe déjà pour ce mandat");
        }
        verifier(d);
        Evenement e = new Evenement(mandatId, slug, d.titre(), d.debutLe());
        appliquer(e, d);
        return evenements.save(e);
    }

    @Transactional
    public Evenement modifier(UUID demandeur, UUID evenementId, Description d) {
        Evenement e = charger(evenementId);
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, e.getMandatId());
        verifier(d);
        appliquer(e, d);
        return e;
    }

    @Transactional
    public Evenement publier(UUID demandeur, UUID evenementId) {
        Evenement e = charger(evenementId);
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, e.getMandatId());
        e.publier();
        return e;
    }

    /**
     * Annule sans supprimer : l'évènement reste sur la page, barré, avec son
     * motif. C'est la seule forme d'annulation qui rende service à quelqu'un
     * qui avait prévu de venir.
     */
    @Transactional
    public Evenement annuler(UUID demandeur, UUID evenementId, String motif) {
        Evenement e = charger(evenementId);
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, e.getMandatId());
        e.annuler(motif, OffsetDateTime.now(horloge));
        return e;
    }

    /**
     * Supprime — réservé aux brouillons. Un évènement déjà publié a été annoncé
     * publiquement ; on l'annule, on ne le fait pas disparaître.
     */
    @Transactional
    public void supprimer(UUID demandeur, UUID evenementId) {
        Evenement e = charger(evenementId);
        politique.exigerSurMandat(demandeur, Permission.EVENEMENT_GERER, e.getMandatId());
        if (e.getStatut() != StatutEvenement.BROUILLON) {
            throw new Erreurs.Conflit(
                    "un évènement déjà publié ne se supprime pas : annulez-le, il restera visible");
        }
        evenements.delete(e);
    }

    // ------------------------------------------------------------- interne

    private Evenement charger(UUID id) {
        return evenements.findById(id)
                .orElseThrow(() -> new Erreurs.Introuvable("évènement", id));
    }

    private void verifier(Description d) {
        if (d.titre() == null || d.titre().isBlank()) {
            throw new Erreurs.RequeteInvalide("le titre d'un évènement est obligatoire");
        }
        if (d.debutLe() == null) {
            throw new Erreurs.RequeteInvalide("la date de début est obligatoire");
        }
        if (d.lien() != null && !LIEN.matcher(d.lien()).matches()) {
            // Un « javascript: » rendu dans un href du portail est une XSS.
            throw new Erreurs.RequeteInvalide("un lien doit être en http(s) : " + d.lien());
        }
        if (d.mediaKey() != null && (d.mediaKey().contains("://") || d.mediaKey().contains("?"))) {
            throw new Erreurs.RequeteInvalide(
                    "media_key est une clé d'objet, pas une URL : " + d.mediaKey());
        }
    }

    private void appliquer(Evenement e, Description d) {
        e.decrire(d.titre(), d.resume(), d.description(), d.lieu(),
                d.debutLe(), d.finLe(), d.mediaKey(), d.lien(), d.complet());
    }

    /** Tout ce qui décrit un évènement, sans son identité ni son statut. */
    public record Description(String titre, String resume, String description, String lieu,
                              OffsetDateTime debutLe, OffsetDateTime finLe,
                              String mediaKey, String lien, boolean complet) { }
}
