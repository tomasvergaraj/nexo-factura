package cl.nexosoftware.factura.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Valida en el arranque (solo perfil "prod") que APP_JWT_SECRET sea seguro:
 * no en blanco, con al menos 32 bytes UTF-8 y distinto del secret de desarrollo.
 * Si no se cumple lanza IllegalStateException y aborta el contexto.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class JwtSecretValidator {

    /**
     * Secret de desarrollo conocido. DEBE mantenerse sincronizado con el default
     * declarado en application-dev.yml (app.jwt.secret).
     */
    static final String DEV_SECRET = "dev-secret-nexo-factura-cambia-esto-en-produccion-0123456789";

    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties appProperties;

    @PostConstruct
    void validar() {
        String secret = appProperties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET es obligatorio en produccion y no puede estar en blanco.");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES
                            + " bytes UTF-8 (actual: " + bytes + ").");
        }
        if (DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET no puede ser el secret de desarrollo conocido en produccion.");
        }
    }
}
