package cl.nexosoftware.factura.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuerpo estandar de error de la API.
 *
 * @param estado    codigo HTTP
 * @param error     nombre del estado HTTP
 * @param mensaje   descripcion legible
 * @param ruta      endpoint que origino el error
 * @param detalles  errores de validacion campo a campo (opcional)
 * @param timestamp instante del error
 */
public record ApiError(
        int estado,
        String error,
        String mensaje,
        String ruta,
        List<CampoInvalido> detalles,
        OffsetDateTime timestamp
) {
    public record CampoInvalido(String campo, String mensaje) {}
}
