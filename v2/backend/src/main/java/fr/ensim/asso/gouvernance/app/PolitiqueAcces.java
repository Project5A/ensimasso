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
 *   <li>Les écritures ne sont permises que sur le mandat <em>en fonction</em>.
 *       Un mandat clos est immuable : c'est ce qui rend l'archive fiable.</li>
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
        if (mandat.getStatut() == StatutMandat.PREPARATION) {
            // Le bureau entrant prépare son site avant l'investiture : il doit
            // pouvoir éditer son propre mandat en préparation.
            return membres.posteActif(mandatId, personneId)
                    .map(mb -> permission.portéePar(mb.getPoste()))
                    .orElse(false);
        }
        return membres.posteActif(mandatId, personneId)
                .map(mb -> permission.portéePar(mb.getPoste()))
                .orElse(false);
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
