package cl.nexosoftware.factura.common.exception;

/** Se lanza cuando una entidad solicitada no existe. Mapea a HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, Object id) {
        return new RecursoNoEncontradoException(entidad + " no encontrado: " + id);
    }
}
