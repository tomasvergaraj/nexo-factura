package cl.nexosoftware.factura.tributario;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Cache del token de sesion del SII, compartido por los dos canales de
 * autenticacion (REST de boleta y SOAP clasico). El token declara 1 hora de
 * vigencia; se renueva proactivamente a los 30 minutos. La renovacion va
 * serializada (double-check bajo lock) para que N emisores concurrentes no
 * quemen N semillas, y {@link #invalidar()} descarta el token cuando el SII
 * responde que ya no autentica.
 */
final class TokenCache {

    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    private volatile Entrada entrada;

    /** Token vigente (cacheado o recien obtenido via {@code renovador}). */
    String obtener(Supplier<String> renovador) {
        Entrada actual = entrada;
        if (vigente(actual)) {
            return actual.token;
        }
        return renovar(renovador);
    }

    /** Descarta el token SOLO si sigue siendo el que fallo (no pisa uno recien renovado). */
    void invalidar(String tokenFallido) {
        Entrada actual = entrada;
        if (actual != null && actual.token.equals(tokenFallido)) {
            entrada = null;
        }
    }

    private synchronized String renovar(Supplier<String> renovador) {
        Entrada actual = entrada;
        if (vigente(actual)) {
            return actual.token; // otro hilo renovo mientras esperabamos el lock
        }
        String token = renovador.get();
        entrada = new Entrada(token, Instant.now());
        return token;
    }

    private static boolean vigente(Entrada e) {
        return e != null && e.obtenido.plus(VIGENCIA).isAfter(Instant.now());
    }

    private record Entrada(String token, Instant obtenido) {}
}
