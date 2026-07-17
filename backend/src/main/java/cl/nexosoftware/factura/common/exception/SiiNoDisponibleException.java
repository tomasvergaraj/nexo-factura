package cl.nexosoftware.factura.common.exception;

/**
 * El SII no esta disponible (caida del servicio, timeout, error de red).
 *
 * En el envio de un DTE NO llega al cliente: {@code DocumentoService} la captura
 * y deja el documento EN_CONTINGENCIA para reintentarlo despues. En operaciones
 * sin cola de contingencia (consulta de estado) propaga y el handler global la
 * traduce a 503.
 */
public class SiiNoDisponibleException extends RuntimeException {

    public SiiNoDisponibleException(String message) {
        super(message);
    }

    public SiiNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
