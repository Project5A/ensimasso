package fr.ensim.asso.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Une seule forme d'erreur pour toute l'API, au format RFC 7807.
 *
 * <p>La v1 n'avait pas de {@code @ControllerAdvice} : chaque contrôleur
 * décidait de son propre code et renvoyait le message brut de l'exception dans
 * le corps — un contrat différent par route, et des détails internes exposés.
 *
 * <p>Ce gestionnaire ne connaît aucun module : il ne traite que les types de
 * {@link Erreurs}, ce qui évite un cycle de dépendances entre {@code shared}
 * et les modules métier.
 */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler({Erreurs.AccesRefuse.class, AccessDeniedException.class})
    public ProblemDetail accesRefuse(Exception e) {
        // Journalisé côté serveur, jamais détaillé côté client : expliquer
        // *pourquoi* c'est refusé renseigne un attaquant sur la structure.
        log.info("accès refusé : {}", e.getMessage());
        return probleme(HttpStatus.FORBIDDEN, "Accès refusé",
                "Vous n'avez pas les droits nécessaires sur cette association.", "acces-refuse");
    }

    @ExceptionHandler(Erreurs.Introuvable.class)
    public ProblemDetail introuvable(Erreurs.Introuvable e) {
        return probleme(HttpStatus.NOT_FOUND, "Introuvable", e.getMessage(), "introuvable");
    }

    @ExceptionHandler(Erreurs.Conflit.class)
    public ProblemDetail conflit(Erreurs.Conflit e) {
        return probleme(HttpStatus.CONFLICT, "Opération impossible", e.getMessage(), "conflit");
    }

    @ExceptionHandler(Erreurs.ContenuInvalide.class)
    public ProblemDetail contenuInvalide(Erreurs.ContenuInvalide e) {
        ProblemDetail pd = probleme(HttpStatus.UNPROCESSABLE_ENTITY, "Contenu invalide",
                e.getMessage(), "contenu-invalide");
        pd.setProperty("erreurs", e.getDetails());
        return pd;
    }

    @ExceptionHandler(Erreurs.RequeteInvalide.class)
    public ProblemDetail requeteInvalide(Erreurs.RequeteInvalide e) {
        return probleme(HttpStatus.BAD_REQUEST, "Requête invalide", e.getMessage(), "requete-invalide");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .sorted().toList();
        ProblemDetail pd = probleme(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Certains champs sont incorrects.", "requete-invalide");
        pd.setProperty("erreurs", details);
        return pd;
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ProblemDetail regleMetier(RuntimeException e) {
        return probleme(HttpStatus.CONFLICT, "Opération impossible", e.getMessage(), "regle-metier");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail imprevu(Exception e) {
        // Seul endroit qui journalise une stacktrace ; le client n'en voit rien.
        log.error("erreur non gérée", e);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur inattendue est survenue.", "erreur-interne");
    }

    private ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(statut, detail);
        pd.setTitle(titre);
        pd.setType(URI.create("https://ensimasso.fr/erreurs/" + code));
        return pd;
    }
}
