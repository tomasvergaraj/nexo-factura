package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.common.exception.DemasiadasPeticionesException;
import cl.nexosoftware.factura.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter en memoria (instancia unica) para autenticacion. Lleva dos cubos:
 * por email (normalizado) y por IP. Cuenta intentos FALLIDOS dentro de una
 * ventana; al alcanzar el maximo bloquea por {@code bloqueoSegundos}. Es
 * thread-safe ({@link ConcurrentHashMap#computeIfAbsent} + {@code synchronized}
 * por entrada) y acota su tamano (barrido amortizado + tope duro fail-open).
 *
 * Limitacion: al ser por instancia, no cubre un despliegue multi-nodo (eso
 * requeriria un store compartido tipo Redis). Para un emisor single-instance es
 * suficiente y evita una dependencia pesada.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final int BARRER_CADA = 500;

    private final RateLimitProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entrada> emails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entrada> ips = new ConcurrentHashMap<>();
    private final AtomicInteger escrituras = new AtomicInteger();

    public RateLimiter(RateLimitProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Lanza 429 si el email o la IP estan bloqueados. No consume presupuesto. */
    public void verificar(String email, String ip) {
        if (!props.enabled()) return;
        long ahora = clock.millis();
        comprobar(emails.get(normalizarEmail(email)), ahora);
        comprobar(ips.get(claveIp(ip)), ahora);
    }

    /** Lanza 429 si la IP esta bloqueada (para /registro). */
    public void verificarIp(String ip) {
        if (!props.enabled()) return;
        comprobar(ips.get(claveIp(ip)), clock.millis());
    }

    private void comprobar(Entrada e, long ahora) {
        if (e != null && e.bloqueado(ahora)) {
            long restanteSeg = Math.max(1, (e.restanteBloqueoMs(ahora) + 999) / 1000);
            throw new DemasiadasPeticionesException(
                    "Demasiados intentos. Reintenta en " + restanteSeg + " segundos.", restanteSeg);
        }
    }

    /** Registra un intento fallido (login): incrementa el cubo de email y el de IP. */
    public void registrarFallo(String email, String ip) {
        if (!props.enabled()) return;
        registrar(emails, normalizarEmail(email), props.maxIntentosEmail());
        registrarFalloIp(ip);
    }

    /** Registra un intento contra el cubo de IP (login fallido o /registro). */
    public void registrarFalloIp(String ip) {
        if (!props.enabled()) return;
        registrar(ips, claveIp(ip), props.maxIntentosIp());
    }

    /** Login exitoso: reinicia el cubo de email (no penaliza a usuarios legitimos). */
    public void registrarExito(String email) {
        if (!props.enabled()) return;
        emails.remove(normalizarEmail(email));
    }

    private void registrar(ConcurrentHashMap<String, Entrada> mapa, String clave, int max) {
        long ahora = clock.millis();
        long ventanaMs = props.ventanaSegundos() * 1000L;
        long bloqueoMs = props.bloqueoSegundos() * 1000L;

        // Tope duro: no crecer mas alla de maxEntradas (fail-open + aviso).
        if (!mapa.containsKey(clave) && mapa.size() >= props.maxEntradas()) {
            log.warn("Rate limiter al tope ({} entradas); no se rastrea la clave", mapa.size());
            return;
        }
        mapa.computeIfAbsent(clave, k -> new Entrada()).fallo(ahora, ventanaMs, max, bloqueoMs);

        if (escrituras.incrementAndGet() % BARRER_CADA == 0) {
            mapa.entrySet().removeIf(en -> en.getValue().expirada(ahora, ventanaMs));
        }
    }

    /** IP del cliente: primer salto de X-Forwarded-For si existe, si no remoteAddr. */
    public String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String claveIp(String ip) {
        return ip == null ? "" : ip;
    }

    /** Limpia todo el estado. Solo para aislamiento entre tests (el bean es singleton). */
    public void reset() {
        emails.clear();
        ips.clear();
        escrituras.set(0);
    }

    /** Estado por clave. Todos los accesos sincronizan sobre la propia instancia. */
    private static final class Entrada {
        private int conteo;
        private long inicioVentanaMs;
        private long bloqueadoHastaMs;

        synchronized boolean bloqueado(long ahora) {
            return bloqueadoHastaMs > ahora;
        }

        synchronized long restanteBloqueoMs(long ahora) {
            return Math.max(0, bloqueadoHastaMs - ahora);
        }

        synchronized void fallo(long ahora, long ventanaMs, int max, long bloqueoMs) {
            if (ahora - inicioVentanaMs > ventanaMs) {
                conteo = 0;
                inicioVentanaMs = ahora;
                bloqueadoHastaMs = 0;
            }
            conteo++;
            if (conteo >= max) {
                bloqueadoHastaMs = ahora + bloqueoMs;
            }
        }

        synchronized boolean expirada(long ahora, long ventanaMs) {
            return bloqueadoHastaMs <= ahora && (ahora - inicioVentanaMs) > ventanaMs;
        }
    }
}
