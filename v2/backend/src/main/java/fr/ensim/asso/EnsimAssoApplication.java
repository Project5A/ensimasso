package fr.ensim.asso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Monolithe modulaire. Les modules ({@code gouvernance}, {@code contenu},
 * {@code adhesion}) ont des frontières vérifiées à la compilation par
 * Spring Modulith + ArchUnit : une violation casse la CI, pas la revue de code.
 *
 * <p>Un seul processus, parce que la passation et la publication doivent être
 * atomiques. Promouvoir un module en service séparé reste un changement de
 * déploiement, pas une réécriture.
 */
@Modulithic(systemName = "ENSIMAsso")
@SpringBootApplication
public class EnsimAssoApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnsimAssoApplication.class, args);
    }
}
