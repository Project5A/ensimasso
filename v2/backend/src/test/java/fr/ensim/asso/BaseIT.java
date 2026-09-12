package fr.ensim.asso;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base des tests d'intégration : un vrai PostgreSQL, avec les migrations
 * Flyway appliquées.
 *
 * <p>Un PostgreSQL en conteneur est indispensable ici et non un détail de
 * confort : l'essentiel des garanties du système — exclusion GiST, index
 * uniques partiels, triggers d'immuabilité, colonne générée — n'existe que
 * dans la base. Les tester avec H2 reviendrait à ne pas les tester.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
public abstract class BaseIT {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ensimasso")
                    .withUsername("ensimasso")
                    .withPassword("ensimasso");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Remet la base à zéro entre deux cas, par TRUNCATE et non par DELETE.
     *
     * <p>Ce n'est pas une préférence de style. Le trigger {@code bloc_fige}
     * refuse tout DELETE sur un bloc appartenant à une version PUBLIEE — c'est
     * précisément la garantie que ces tests existent pour démontrer. Un cas qui
     * publie une version laisse donc derrière lui une ligne indestructible, et
     * le nettoyage du cas suivant échoue. La suite s'empoisonnait elle-même :
     * les deux premiers cas passaient, les cinq suivants mouraient dans leur
     * {@code @BeforeEach}, et cela depuis que ces tests existent.
     *
     * <p>TRUNCATE agit au niveau de l'instruction et ne déclenche pas les
     * triggers {@code FOR EACH ROW}. Le garde-fou de production reste donc
     * pleinement actif — on ne le désactive pas, on emprunte une opération
     * qu'il ne couvre pas et n'a jamais eu vocation à couvrir : empêcher le
     * code applicatif de réécrire l'histoire n'a rien à voir avec remettre un
     * banc d'essai à zéro.
     *
     * <p>La liste est calculée, pas écrite à la main : une migration qui ajoute
     * une table la videra sans qu'on y pense. {@code type_bloc} est préservée —
     * c'est le registre peuplé par les migrations, pas de la donnée de test.
     */
    protected void viderLesTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT c.relname FROM pg_class c "
              + "JOIN pg_namespace n ON n.oid = c.relnamespace "
              + "WHERE n.nspname = 'public' AND c.relkind = 'r' "
              + "AND c.relname NOT IN ('type_bloc', 'flyway_schema_history')",
                String.class);
        if (tables.isEmpty()) {
            throw new IllegalStateException(
                    "aucune table à vider : les migrations ont-elles été appliquées ?");
        }
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:0/realms/test");
    }
}
