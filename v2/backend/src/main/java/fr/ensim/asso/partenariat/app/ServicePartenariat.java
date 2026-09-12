package fr.ensim.asso.partenariat.app;

import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.partenariat.domain.*;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Les partenaires d'un mandat. */
@Service
public class ServicePartenariat {

    private static final Pattern LIEN = Pattern.compile("^https?://\\S+$");

    private final PartenaireRepository partenaires;
    private final PolitiqueAcces politique;

    public ServicePartenariat(PartenaireRepository partenaires, PolitiqueAcces politique) {
        this.partenaires = partenaires;
        this.politique = politique;
    }

    @Transactional(readOnly = true)
    public List<Partenaire> visiblesDuMandat(UUID mandatId) {
        return partenaires.findByMandatIdAndVisibleTrue(mandatId).stream()
                .sorted(Partenaire.AFFICHAGE).toList();
    }

    @Transactional(readOnly = true)
    public List<Partenaire> tousDuMandat(UUID demandeur, UUID mandatId) {
        politique.exigerSurMandat(demandeur, Permission.PARTENAIRE_GERER, mandatId);
        return partenaires.findByMandatId(mandatId).stream().sorted(Partenaire.AFFICHAGE).toList();
    }

    @Transactional
    public Partenaire creer(UUID demandeur, UUID mandatId, Description d) {
        politique.exigerSurMandat(demandeur, Permission.PARTENAIRE_GERER, mandatId);
        verifier(d);
        if (partenaires.existsByMandatIdAndNom(mandatId, d.nom())) {
            throw new Erreurs.Conflit("« " + d.nom() + " » est déjà partenaire de ce mandat");
        }
        Partenaire p = new Partenaire(mandatId, d.nom(), d.niveau());
        p.decrire(d.nom(), d.niveau(), d.logoMediaKey(), d.url(), d.ordre(), d.visible());
        return partenaires.save(p);
    }

    @Transactional
    public Partenaire modifier(UUID demandeur, UUID partenaireId, Description d) {
        Partenaire p = charger(partenaireId);
        politique.exigerSurMandat(demandeur, Permission.PARTENAIRE_GERER, p.getMandatId());
        verifier(d);
        p.decrire(d.nom(), d.niveau(), d.logoMediaKey(), d.url(), d.ordre(), d.visible());
        return p;
    }

    /**
     * Supprimable sans condition, contrairement à un évènement : un partenariat
     * qui n'a pas eu lieu n'a rien annoncé à personne. La page d'archive du
     * mandat précédent n'est pas touchée — elle a ses propres lignes.
     */
    @Transactional
    public void supprimer(UUID demandeur, UUID partenaireId) {
        Partenaire p = charger(partenaireId);
        politique.exigerSurMandat(demandeur, Permission.PARTENAIRE_GERER, p.getMandatId());
        partenaires.delete(p);
    }

    // ------------------------------------------------------------- interne

    private Partenaire charger(UUID id) {
        return partenaires.findById(id)
                .orElseThrow(() -> new Erreurs.Introuvable("partenaire", id));
    }

    private void verifier(Description d) {
        if (d.nom() == null || d.nom().isBlank()) {
            throw new Erreurs.RequeteInvalide("le nom d'un partenaire est obligatoire");
        }
        if (d.niveau() == null) {
            throw new Erreurs.RequeteInvalide("le niveau d'un partenaire est obligatoire");
        }
        if (d.url() != null && !LIEN.matcher(d.url()).matches()) {
            throw new Erreurs.RequeteInvalide("un lien doit être en http(s) : " + d.url());
        }
        if (d.logoMediaKey() != null
                && (d.logoMediaKey().contains("://") || d.logoMediaKey().contains("?"))) {
            throw new Erreurs.RequeteInvalide(
                    "le logo est une clé d'objet, pas une URL : " + d.logoMediaKey());
        }
    }

    public record Description(String nom, NiveauPartenaire niveau, String logoMediaKey,
                              String url, int ordre, boolean visible) { }
}
