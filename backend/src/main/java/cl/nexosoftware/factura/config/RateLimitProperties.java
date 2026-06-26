package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del rate limiting de autenticacion (prefijo
 * {@code app.security.rate-limit}). Cuenta intentos FALLIDOS por email y por IP
 * dentro de una ventana; al alcanzar el maximo bloquea por {@code bloqueoSegundos}.
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int maxIntentosEmail,
        int maxIntentosIp,
        long ventanaSegundos,
        long bloqueoSegundos,
        int maxEntradas
) {
    public RateLimitProperties {
        if (maxIntentosEmail <= 0) maxIntentosEmail = 5;
        if (maxIntentosIp <= 0) maxIntentosIp = 20;
        if (ventanaSegundos <= 0) ventanaSegundos = 900;
        if (bloqueoSegundos <= 0) bloqueoSegundos = 900;
        if (maxEntradas <= 0) maxEntradas = 50_000;
    }
}
