package fr.ensim.asso.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/**
 * L'horloge est injectée plutôt qu'appelée en statique : un domaine dont tout
 * dépend de dates (mandats, années, expiration d'adhésions) doit être testable
 * à une date arbitraire, sans attendre septembre.
 */
@Configuration
public class ConfigurationHorloge {

    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }
}
