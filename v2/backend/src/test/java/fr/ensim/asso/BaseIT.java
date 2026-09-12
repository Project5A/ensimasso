package fr.ensim.asso;

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
