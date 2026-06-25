package cl.nexosoftware.factura.common.exception;

/** Violacion de una regla de negocio (ej: folio agotado, estado invalido). Mapea a HTTP 409. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
