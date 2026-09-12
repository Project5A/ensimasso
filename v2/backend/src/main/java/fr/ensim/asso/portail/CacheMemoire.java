package fr.ensim.asso.portail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache en mémoire, par instance.
 *
 * <p>C'est le défaut, et il ne demande aucune infrastructure : une association
 * qui déploie une seule instance obtient déjà l'essentiel du bénéfice, puisque
 * le trafic se concentre sur une poignée de pages d'accueil. Avec plusieurs
 * répliques, chacune chauffe la sienne — moins efficace qu'un cache partagé,
 * jamais incohérent, puisque les clés sont versionnées.
 *
 * <p>Borné en nombre d'entrées : un cache sans borne est une fuite de mémoire
 * dont on ne s'aperçoit qu'en production, et le nombre de pages publiques est
 * connu et petit.
 */
@Component
@ConditionalOnProperty(name = "ensimasso.cache.type", havingValue = "memoire", matchIfMissing = true)
public class CacheMemoire implements PortCache {

    private record Entree(String valeur, Instant expireLe) { }

    private final int capacite;
    private final Clock horloge;
    private final Map<String, Entree> entrees;

    private final AtomicLong succes = new AtomicLong();
    private final AtomicLong echecs = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public CacheMemoire(Clock horloge) {
        this(horloge, 500);
    }

    CacheMemoire(Clock horloge, int capacite) {
        this.horloge = horloge;
        this.capacite = capacite;
        this.entrees = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entree> plusAncienne) {
                if (size() > CacheMemoire.this.capacite) {
                    evictions.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public synchronized Optional<String> lire(String cle) {
        Entree entree = entrees.get(cle);
        if (entree == null) {
            echecs.incrementAndGet();
            return Optional.empty();
        }
        if (!entree.expireLe().isAfter(Instant.now(horloge))) {
            entrees.remove(cle);
            echecs.incrementAndGet();
            return Optional.empty();
        }
        succes.incrementAndGet();
        return Optional.of(entree.valeur());
    }

    @Override
    public synchronized void ecrire(String cle, String valeur, Duration duree) {
        entrees.put(cle, new Entree(valeur, Instant.now(horloge).plus(duree)));
    }

    @Override
    public synchronized Statistiques statistiques() {
        return new Statistiques(succes.get(), echecs.get(), evictions.get(), entrees.size());
    }
}
