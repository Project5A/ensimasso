package fr.ensim.asso.contenu.app;

import fr.ensim.asso.contenu.domain.StatutVersion;
import fr.ensim.asso.contenu.domain.ThemeVersion;
import fr.ensim.asso.contenu.domain.ThemeVersionRepository;
import fr.ensim.asso.gouvernance.app.PolitiqueAcces;
import fr.ensim.asso.gouvernance.domain.Permission;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Le thème d'un mandat : ses couleurs, ses polices, son identité visuelle.
 *
 * <p>Tout existait sauf ce service. La table {@code theme_version} et son index
 * « une seule version publiée par mandat », l'entité avec ses transitions
 * {@code publier()} et {@code archiver()}, le clonage à la passation, le rendu
 * par le portail public, et jusqu'à la permission {@code THEME_EDITER} avec sa
 * table de vérité — tout était écrit. Il manquait le seul morceau par lequel un
 * humain peut agir : aucune route, aucune méthode de service ne créait, ne
 * modifiait ni ne publiait un thème. {@code publier()} n'était appelée de nulle
 * part, {@code THEME_EDITER} n'était consultée nulle part.
 *
 * <p>Conséquence concrète : un thème publié ne pouvait exister que par un
 * INSERT à la main en base. Et à chaque passation, {@code clonerTheme} déposait
 * pour le bureau entrant un BROUILLON que personne ne pouvait publier — donc
 * une association perdait son identité visuelle à chaque changement de bureau,
 * définitivement.
 */
@Service
public class ServiceTheme {

    private final ThemeVersionRepository themes;
    private final PolitiqueAcces politique;

    public ServiceTheme(ThemeVersionRepository themes, PolitiqueAcces politique) {
        this.themes = themes;
        this.politique = politique;
    }

    /**
     * Le thème sur lequel travailler : le brouillon s'il existe, sinon le
     * thème publié. Réservé au bureau du mandat.
     */
    @Transactional(readOnly = true)
    public Optional<ThemeVersion> aEditer(UUID demandeur, UUID mandatId) {
        politique.exigerMembre(demandeur, mandatId);
        Optional<ThemeVersion> brouillon = themes.brouillon(mandatId);
        return brouillon.isPresent() ? brouillon : themes.versionPubliee(mandatId);
    }

    /** Le thème en vigueur pour ce mandat, s'il y en a un. */
    @Transactional(readOnly = true)
    public Optional<ThemeVersion> enVigueur(UUID demandeur, UUID mandatId) {
        politique.exigerMembre(demandeur, mandatId);
        return themes.versionPubliee(mandatId);
    }

    /**
     * Enregistre le brouillon de thème. Idempotent : un seul brouillon vit à la
     * fois par mandat, on le réécrit plutôt que d'en empiler.
     */
    @Transactional
    public ThemeVersion enregistrerBrouillon(UUID demandeur, UUID mandatId, String tokensJson) {
        politique.exigerSurMandat(demandeur, Permission.THEME_EDITER, mandatId);

        return themes.brouillon(mandatId)
                .map(b -> { b.remplacerTokens(tokensJson); return b; })
                .orElseGet(() -> themes.save(new ThemeVersion(
                        mandatId, themes.dernierNumero(mandatId) + 1, tokensJson)));
    }

    /**
     * Publie le brouillon et archive le thème précédent.
     *
     * <p>Dans cet ordre, avec un flush entre les deux : l'index partiel
     * {@code theme_une_seule_publiee} n'admet qu'une version PUBLIEE par
     * mandat, et publier avant d'archiver le violerait. C'est la même séquence
     * que la publication d'une page, pour la même raison.
     */
    @Transactional
    public ThemeVersion publier(UUID demandeur, UUID mandatId) {
        politique.exigerSurMandat(demandeur, Permission.THEME_EDITER, mandatId);

        ThemeVersion brouillon = themes.brouillon(mandatId)
                .orElseThrow(() -> new Erreurs.Conflit(
                        "aucun brouillon de thème à publier pour ce mandat"));

        themes.versionPubliee(mandatId).ifPresent(ThemeVersion::archiver);
        themes.flush();

        brouillon.publier();
        return brouillon;
    }

    /** Le statut d'une version, exposé pour l'API. */
    public static StatutVersion statutDe(ThemeVersion v) {
        return v.getStatut();
    }
}
