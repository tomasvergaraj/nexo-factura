package cl.nexosoftware.factura.common.exception;

/**
 * Se excedio el limite de intentos (rate limiting). Se traduce a HTTP 429 con un
 * header {@code Retry-After} construido a partir de {@link #getRetryAfterSegundos()}.
 */
public class DemasiadasPeticionesException extends RuntimeException {

    private final long retryAfterSegundos;

    public DemasiadasPeticionesException(String mensaje, long retryAfterSegundos) {
        super(mensaje);
        this.retryAfterSegundos = retryAfterSegundos;
    }

    public long getRetryAfterSegundos() {
        return retryAfterSegundos;
    }
}
