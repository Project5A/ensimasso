package fr.ensim.asso.contenu.app;

import fr.ensim.asso.contenu.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Clonage du contenu d'un mandat vers un autre, utilisé par la passation.
 *
 * <p>Exposé par le module contenu pour que l'orchestration reste une seule
 * transaction locale. Cloner via un évènement asynchrone donnerait l'échec que
 * la revue a nommé : « nouveau bureau, droits complets, site vide, le
 * 1er septembre ».
 */
@Service
public class ClonageContenu {

    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final BlocRepository blocs;
    private final ThemeVersionRepository themes;

    public ClonageContenu(PageRepository pages, PageVersionRepository versions,
                          BlocRepository blocs, ThemeVersionRepository themes) {
        this.pages = pages;
        this.versions = versions;
        this.blocs = blocs;
        this.themes = themes;
    }

    /**
     * Copie chaque page publiée du mandat source vers le mandat cible, sous
     * forme de BROUILLON. Le bureau entrant part du site existant et le
     * retravaille ; rien n'est publié tant qu'il ne le décide pas.
     *
     * @return le nombre de pages clonées
     */
    @Transactional
    public int clonerPagesPubliees(UUID mandatSource, UUID mandatCible, UUID auteur) {
        int clonees = 0;
        for (Page source : pages.findByMandatIdOrderByOrdreMenuAsc(mandatSource)) {
            PageVersion publiee = versions.versionPubliee(source.getId()).orElse(null);
            if (publiee == null) {
                continue;                       // page jamais publiée : on l'ignore
            }
            Page copie = pages.save(new Page(
                    mandatCible, source.getSlug(), source.getTitre(), source.getOrdreMenu()));

            PageVersion brouillon = versions.save(new PageVersion(copie.getId(), 1, auteur));
            brouillon.setNote("cloné depuis le mandat précédent");

            List<Bloc> contenu = blocs.findByPageVersionIdOrderByOrdreAsc(publiee.getId());
            for (Bloc src : contenu) {
                // On conserve la version de schéma d'origine : le contenu reste
                // valide même si le schéma courant s'est durci depuis.
                blocs.save(new Bloc(brouillon.getId(), src.getOrdre(), src.getType(),
                        src.getSchemaVersion(), src.getPayload()));
            }
            clonees++;
        }
        return clonees;
    }

    /** Le thème est cloné : un nouveau bureau part de l'identité existante. */
    @Transactional
    public void clonerTheme(UUID mandatSource, UUID mandatCible) {
        themes.versionPubliee(mandatSource).ifPresent(src -> {
            ThemeVersion copie = new ThemeVersion(mandatCible, 1, src.getTokens());
            themes.save(copie);
        });
    }
}
