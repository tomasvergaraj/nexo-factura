package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({AppProperties.class, RateLimitProperties.class})
public class PropertiesConfig {

    /** Reloj inyectable (el RateLimiter lo usa; los tests pueden sustituirlo). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
