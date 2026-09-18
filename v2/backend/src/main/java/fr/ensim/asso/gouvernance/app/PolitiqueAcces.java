package fr.ensim.asso.gouvernance.app;

import fr.ensim.asso.gouvernance.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Le point unique où se décide « cette personne peut-elle faire cette action
 * sur cette association ? ».
 *
 * <p>Deux règles non négociables, tirées directement de l'audit de la v1 :
 * <ol>
 *   <li>Aucune écriture sur un mandat <strong>CLOS</strong>. Un mandat clos est
 *       immuable : c'est ce qui rend l'archive fiable. Un mandat en
 *       PRÉPARATION, lui, accepte les écritures — le bureau entrant doit
 *       pouvoir composer son site avant l'investiture, et c'est tout l'objet de
 *       ce statut. (Cette phrase disait « seulement sur le mandat en
 *       fonction », ce que le code n'a jamais fait ; elle décrivait donc une
 *       règle plus stricte que celle appliquée, sur la classe même où l'on
 *       vient vérifier ce qui est permis.)</li>
 *   <li>L'appartenance n'est jamais lue depuis le jeton. Le jeton porte
 *       l'identité ; les droits par association sont résolus ici, pour être
 *       révocables immédiatement.</li>
 * </ol>
 */
@Component
public class PolitiqueAcces {

    private final MandatRepository mandats;
    private final MembreBureauRepository membres;

    public PolitiqueAcces(MandatRepository mandats, MembreBureauRepository membres) {
        this.mandats = mandats;
        this.membres = membres;
    }

    /** Autorisation sur le mandat actuellement en fonction d'une association. */
    @Transactional(readOnly = true)
    public boolean peut(UUID personneId, Permission permission, UUID associationId) {
        if (personneId == null || associationId == null) {
            return false;
        }
        return mandats.mandatEnFonction(associationId)
                .flatMap(m -> membres.posteActif(m.getId(), personneId))
                .map(mb -> permission.portéePar(mb.getPoste()))
                .orElse(false);
    }

    /**
     * Autorisation sur un mandat nommément désigné — utilisé par les écritures
     * de contenu, qui portent toujours sur un mandat précis.
     */
    @Transactional(readOnly = true)
    public boolean peutSurMandat(UUID personneId, Permission permission, UUID mandatId) {
        if (personneId == null || mandatId == null) {
            return false;
        }
        Mandat mandat = mandats.findById(mandatId).orElse(null);
        if (mandat == null || !mandat.accepteEcriture()) {
            return false;   // un mandat CLOS n'accepte plus aucune écriture
        }
        // PRÉPARATION et EN_FONCTION suivent la MÊME règle : siéger à ce
        // mandat-là, avec un poste qui porte la permission. Une branche
        // séparée existait pour le cas PRÉPARATION, à l'identique près du
        // commentaire — elle donnait à croire à un traitement particulier là où
        // il n'y en a pas, et masquait que la seule question posée par cette
        // méthode est celle du mandat CLOS.
        //
        // Le bureau ENTRANT compose bien son site avant l'investiture ; c'est
        // l'objet du statut PRÉPARATION, et c'est pour cela qu'on ne le refuse
        // pas ici.
        return membres.posteActif(mandatId, personneId)
                .map(mb -> permission.portéePar(mb.getPoste()))
                .orElse(false);
    }

    /**
     * Cette personne siège-t-elle au bureau de ce mandat ?
     *
     * <p>Distinct d'une permission : il s'agit d'appartenance, pas de droit
     * d'agir. C'est le contrôle qui manquait aux routes de LECTURE du tableau
     * de bord, lesquelles n'en exerçaient aucun — n'importe quel compte
     * authentifié pouvait lire les brouillons de n'importe quelle association,
     * et la composition d'un bureau entrant avant son annonce en assemblée.
     */
    @Transactional(readOnly = true)
    public boolean estMembre(UUID personneId, UUID mandatId) {
        if (personneId == null || mandatId == null) {
            return false;
        }
        return membres.posteActif(mandatId, personneId).isPresent();
    }

    public void exigerMembre(UUID personneId, UUID mandatId) {
        if (!estMembre(personneId, mandatId)) {
            throw new Erreurs.AccesRefuse(
                    "il faut siéger au bureau de ce mandat pour en lire le contenu");
        }
    }

    /** Lance une exception si l'action n'est pas permise. */
    public void exigerSurMandat(UUID personneId, Permission permission, UUID mandatId) {
        if (!peutSurMandat(personneId, permission, mandatId)) {
            throw new Erreurs.AccesRefuse("permission " + permission + " refusee sur le mandat " + mandatId);
        }
    }

    public void exiger(UUID personneId, Permission permission, UUID associationId) {
        if (!peut(personneId, permission, associationId)) {
            throw new Erreurs.AccesRefuse("permission " + permission + " refusee sur association " + associationId);
        }
    }

}
