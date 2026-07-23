package cl.nexosoftware.factura.tributario;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache del token de sesion del SII, compartido por los dos canales de
 * autenticacion (REST de boleta y SOAP clasico), PARTICIONADO por identidad de
 * certificado (huella SHA-256): el token del SII es una sesion del CERTIFICADO
 * que firmo la semilla, no de la aplicacion. En modo GLOBAL todas las empresas
 * comparten la unica huella (un token, como siempre); en POR_EMPRESA cada
 * certificado tiene su sesion — y si un mismo firmante (contador) sirve a
 * varias empresas con el mismo PKCS#12, comparten sesion sin quemar semillas
 * de mas.
 *
 * El token declara 1 hora de vigencia; se renueva proactivamente a los 30
 * minutos. La renovacion va serializada POR CLAVE (compute atomico) para que N
 * emisores concurrentes del mismo certificado no quemen N semillas, y
 * {@link #invalidar} descarta el token cuando el SII responde que ya no
 * autentica.
 */
final class TokenCache {

    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();

    /** Token vigente de esa identidad (cacheado o recien obtenido via {@code renovador}). */
    String obtener(String clave, Supplier<String> renovador) {
        Entrada actual = entradas.get(clave);
        if (vigente(actual)) {
            return actual.token;
        }
        // compute es atomico por clave: un solo hilo renueva; los demas del
        // mismo certificado esperan y reciben el token recien emitido.
        return entradas.compute(clave, (k, previa) ->
                vigente(previa) ? previa : new Entrada(renovador.get(), Instant.now())).token;
    }

    /** Descarta el token SOLO si sigue siendo el que fallo (no pisa uno recien renovado). */
    void invalidar(String clave, String tokenFallido) {
        entradas.computeIfPresent(clave,
                (k, actual) -> actual.token.equals(tokenFallido) ? null : actual);
    }

    private static boolean vigente(Entrada e) {
        return e != null && e.obtenido.plus(VIGENCIA).isAfter(Instant.now());
    }

    private record Entrada(String token, Instant obtenido) {}
}
