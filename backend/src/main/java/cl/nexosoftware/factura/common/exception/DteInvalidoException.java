package cl.nexosoftware.factura.common.exception;

import java.util.List;

/**
 * El XML del DTE generado por el sistema NO cumple el esquema XSD antes de
 * firmarse. A diferencia de {@link ReglaNegocioException}, no es un error del
 * cliente: la peticion era valida y la entidad se construyo, pero el XML que el
 * servidor genero fallo la validacion de esquema, sintoma de un bug en el
 * modelo/generador (ModeloDte / XmlDteGenerator). Mapea a HTTP 422 y expone los
 * errores de esquema en ApiError.detalles para diagnostico.
 */
public class DteInvalidoException extends RuntimeException {

    private final transient List<ApiError.CampoInvalido> errores;

    public DteInvalidoException(String mensaje, List<ApiError.CampoInvalido> errores) {
        super(mensaje);
        this.errores = errores;
    }

    public List<ApiError.CampoInvalido> getErrores() {
        return errores;
    }
}
