package fr.ensim.asso.portail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache partagé, pour le profil {@code delivery} en plusieurs répliques.
 *
 * <p>Valkey parle le protocole Redis : le client est le même, et l'adaptateur
 * se teste indifféremment contre l'un ou l'autre — ce qui est la raison pour
 * laquelle il existe ici plutôt que dans une note d'architecture.
 *
 * <p>Une panne du cache ne doit pas faire tomber le site : toute erreur est
 * avalée et comptée comme un échec de lecture. Le pire cas est de servir les
 * pages en interrogeant la base, c'est-à-dire ce que fait le système sans
 * cache.
 */
@Component
@ConditionalOnProperty(name = "ensimasso.cache.type", havingValue = "valkey")
public class CacheValkey implements PortCache {

    private final StringRedisTemplate redis;
    private final AtomicLong succes = new AtomicLong();
    private final AtomicLong echecs = new AtomicLong();
    private final AtomicLong pannes = new AtomicLong();

    public CacheValkey(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> lire(String cle) {
        try {
            String valeur = redis.opsForValue().get(cle);
            if (valeur == null) {
                echecs.incrementAndGet();
                return Optional.empty();
            }
            succes.incrementAndGet();
            return Optional.of(valeur);
        } catch (RuntimeException e) {
            pannes.incrementAndGet();
            echecs.incrementAndGet();
            return Optional.empty();
        }
    }

    @Override
    public void ecrire(String cle, String valeur, Duration duree) {
        try {
            redis.opsForValue().set(cle, valeur, duree);
        } catch (RuntimeException e) {
            pannes.incrementAndGet();   // écrire est un confort, pas une obligation
        }
    }

    @Override
    public Statistiques statistiques() {
        // Le nombre d'entrées n'est pas demandé au serveur : un KEYS ou un
        // DBSIZE sur une base partagée coûte plus cher que le renseignement.
        return new Statistiques(succes.get(), echecs.get(), pannes.get(), -1);
    }
}
