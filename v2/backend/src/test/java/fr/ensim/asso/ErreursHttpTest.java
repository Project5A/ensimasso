package fr.ensim.asso;

import fr.ensim.asso.shared.error.GestionnaireErreurs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que l'API répond quand la REQUÊTE est fautive.
 *
 * <p>Le gestionnaire traite les exceptions du domaine et la validation des
 * champs, puis attrape tout le reste en 500. Or « tout le reste » contient les
 * erreurs de liaison de Spring MVC : un corps JSON malformé, un UUID illisible
 * dans le chemin, un paramètre requis absent. Ce sont des fautes du CLIENT. Les
 * rendre en 500 les fait passer pour des pannes du serveur : elles remontent
 * dans les alertes, elles font réessayer l'appelant, et elles n'apprennent rien
 * à celui qui s'est trompé.
 */
class ErreursHttpTest {

    /** Un contrôleur minimal, juste pour provoquer les erreurs de liaison. */
    @RestController
    static class Cobaye {
        record Corps(String nom) { }

        @GetMapping("/cobaye/{id}")
        public String parId(@PathVariable UUID id) { return id.toString(); }

        @GetMapping("/cobaye")
        public String avecParametre(@RequestParam String obligatoire) { return obligatoire; }

        @PostMapping("/cobaye")
        public String corps(@RequestBody Corps corps) { return corps.nom(); }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Cobaye())
            .setControllerAdvice(new GestionnaireErreurs())
            .build();

    @Test
    @DisplayName("un identifiant illisible dans le chemin est une faute du client : 400")
    void identifiantIllisible() throws Exception {
        mvc.perform(get("/cobaye/pas-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"));
    }

    @Test
    @DisplayName("un corps JSON malformé est une faute du client : 400")
    void corpsMalforme() throws Exception {
        mvc.perform(post("/cobaye")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ceci n'est pas du JSON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un paramètre obligatoire absent est une faute du client : 400")
    void parametreAbsent() throws Exception {
        mvc.perform(get("/cobaye"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la réponse d'erreur ne divulgue jamais l'intérieur du serveur")
    void aucuneFuite() throws Exception {
        mvc.perform(get("/cobaye/pas-un-uuid"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                // Ni nom de classe, ni chemin de paquet, ni trace d'appel.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("fr.ensim.asso"))))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));
    }

    // ------------------------------- et quand c'est la RESSOURCE qui manque

    @Test
    @DisplayName("une association inconnue est une ressource absente : 404, pas 409")
    void associationInconnueEn404() throws Exception {
        var associations = org.mockito.Mockito.mock(
                fr.ensim.asso.gouvernance.domain.AssociationRepository.class);
        org.mockito.Mockito.when(associations.findBySlug(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        MockMvc api = MockMvcBuilders
                .standaloneSetup(new fr.ensim.asso.gouvernance.api.GouvernanceController(
                        associations,
                        org.mockito.Mockito.mock(fr.ensim.asso.gouvernance.domain.MandatRepository.class),
                        org.mockito.Mockito.mock(
                                fr.ensim.asso.gouvernance.domain.MembreBureauRepository.class),
                        org.mockito.Mockito.mock(fr.ensim.asso.gouvernance.app.PolitiqueAcces.class)))
                .setControllerAdvice(new GestionnaireErreurs())
                .build();

        // La route levait IllegalArgumentException, qui tombe dans le
        // fourre-tout « règle métier » et sort en 409 « Opération impossible ».
        // Un client ne pouvait pas distinguer « ce slug n'existe pas » de
        // « l'opération est refusée » — et un 409 invite à réessayer là où un
        // 404 dit de ne pas insister.
        api.perform(get("/api/gouvernance/associations/inconnue/mandats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Introuvable"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("inconnue")));
    }
}
