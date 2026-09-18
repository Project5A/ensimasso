package fr.ensim.asso.portail;

import io.lettuce.core.RedisConnectionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * L'adaptateur Valkey, contre un vrai serveur.
 *
 * <p>Valkey parle le protocole Redis : ce test s'exécute indifféremment contre
 * l'un ou l'autre. Il <strong>échoue</strong> si aucun serveur n'est joignable,
 * plutôt que de se désactiver en silence — un test qui s'ignore lui-même donne
 * la même couleur qu'un test qui passe, et c'est ainsi qu'on croit avoir
 * vérifié un adaptateur qu'on n'a jamais exécuté.
 */
class CacheValkeyIT {

    private static final String HOTE = System.getenv().getOrDefault("VALKEY_HOTE", "localhost");
    private static final int PORT =
            Integer.parseInt(System.getenv().getOrDefault("VALKEY_PORT", "6379"));

    private static LettuceConnectionFactory fabrique;
    private static StringRedisTemplate redis;
    private static CacheValkey cache;

    private final String prefixe = "test:" + UUID.randomUUID() + ':';

    @BeforeAll
    static void demarrer() {
        fabrique = new LettuceConnectionFactory(new RedisStandaloneConfiguration(HOTE, PORT));
        fabrique.afterPropertiesSet();
        redis = new StringRedisTemplate(fabrique);
        redis.afterPropertiesSet();
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> c.ping());
        } catch (RuntimeException e) {
            // Les parenthèses ne sont pas cosmétiques : `.formatted(…)` ne
            // s'appliquait qu'au DERNIER littéral de la concaténation. Le
            // message affichait donc « sur %s:%d » en toutes lettres et
            // rapportait l'hôte comme cause — sur le seul message dont
            // quelqu'un dispose pour comprendre pourquoi la CI est rouge.
            fail(("aucun serveur Valkey/Redis sur %s:%d — ce test doit échouer plutôt que "
                + "prétendre avoir vérifié l'adaptateur (cause : %s)")
                    .formatted(HOTE, PORT, e.getClass().getSimpleName()), e);
        }
        cache = new CacheValkey(redis);
    }

    @AfterAll
    static void arreter() {
        if (fabrique != null) {
            fabrique.destroy();
        }
    }

    @Test
    @DisplayName("une valeur écrite est relue à l'identique")
    void allerRetour() {
        String cle = prefixe + "page";
        cache.ecrire(cle, "{\"titre\":\"Accueil\"}", Duration.ofMinutes(5));

        assertThat(cache.lire(cle)).contains("{\"titre\":\"Accueil\"}");
        assertThat(cache.statistiques().succes()).isPositive();
    }

    @Test
    @DisplayName("la durée demandée est bien appliquée côté serveur")
    void dureeAppliquee() {
        String cle = prefixe + "ttl";
        cache.ecrire(cle, "v", Duration.ofSeconds(60));

        Long restant = redis.getExpire(cle);
        assertThat(restant)
                .as("une entrée sans expiration resterait indéfiniment, et servirait "
                  + "des URL de médias mortes")
                .isNotNull()
                .isBetween(50L, 60L);
    }

    @Test
    @DisplayName("une clé absente est un échec de lecture, pas une erreur")
    void cleAbsente() {
        assertThat(cache.lire(prefixe + "jamais-ecrite")).isEmpty();
    }

    @Test
    @DisplayName("une panne du cache ne fait pas tomber le site")
    void panneToleree() {
        // Le pire cas acceptable est de servir les pages en interrogeant la
        // base — c'est-à-dire le comportement sans cache.
        LettuceConnectionFactory injoignable =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(HOTE, 6399));
        injoignable.afterPropertiesSet();
        StringRedisTemplate mort = new StringRedisTemplate(injoignable);
        mort.afterPropertiesSet();
        CacheValkey enPanne = new CacheValkey(mort);

        try {
            assertThat(enPanne.lire("peu-importe")).isEmpty();
            enPanne.ecrire("peu-importe", "v", Duration.ofMinutes(1));
            assertThat(enPanne.statistiques().evictions())
                    .as("les pannes sont comptées, pas propagées")
                    .isPositive();
        } catch (RedisConnectionException e) {
            fail("l'adaptateur doit avaler la panne, pas la propager", e);
        } finally {
            injoignable.destroy();
        }
    }

    @Test
    @DisplayName("le rendu d'une page fait l'aller-retour sans perte")
    void pageRendueSerialisable() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        PageRendue page = new PageRendue(
                new PageRendue.AssociationVue("bde", "Bureau des Élèves", "BUREAU"),
                new PageRendue.MandatVue("2025-2026", "EN_FONCTION", true),
                "accueil", "Accueil", 4, java.time.OffsetDateTime.now(),
                java.util.Map.of("accent", "#1f4e79"),
                java.util.List.of(new PageRendue.BlocRendu(
                        UUID.randomUUID(), "HERO", 1,
                        java.util.Map.of("titre", "Bonjour"), java.util.Map.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of())),
                java.util.List.of(new PageRendue.PageLien("accueil", "Accueil", 0)),
                java.util.List.of("2025-2026"), "2025-2026");

        String cle = prefixe + "rendue";
        cache.ecrire(cle, mapper.writeValueAsString(page), Duration.ofMinutes(5));

        PageRendue relue = mapper.readValue(cache.lire(cle).orElseThrow(), PageRendue.class);
        assertThat(relue).isEqualTo(page);
    }
}
