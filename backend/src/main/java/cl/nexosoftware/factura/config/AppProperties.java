package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades de aplicacion (prefijo "app" en application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Sii sii) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    /** Ambiente del SII: CERTIFICACION (Maullin) o PRODUCCION (Palena). */
    public record Sii(String ambiente, String certificadoPath, String certificadoPassword) {}
}
