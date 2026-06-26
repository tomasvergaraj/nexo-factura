package cl.nexosoftware.factura.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/** Traduce excepciones a respuestas {@link ApiError} consistentes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DteInvalidoException.class)
    public ResponseEntity<ApiError> dteInvalido(DteInvalidoException ex, HttpServletRequest req) {
        // El XML lo genera el servidor: un fallo de esquema es un bug del
        // modelo/generador, no del cliente. Se registra y se devuelve 422 con el
        // detalle de cada error de esquema para diagnostico.
        log.error("DTE genera XML invalido contra el XSD en {} {}: {}",
                req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, ex.getErrores());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> conflictoDeVersion(OptimisticLockingFailureException ex, HttpServletRequest req) {
        // El registro fue modificado por otra operacion concurrente (lost update evitado).
        return build(HttpStatus.CONFLICT,
                "El registro fue modificado por otra operacion; recargue y reintente", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        // Violacion de restriccion (tipicamente unicidad: RUT o codigo duplicado).
        log.warn("Violacion de integridad en {} {}: {}",
                req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "El registro infringe una restriccion de unicidad o integridad (posible duplicado)", req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> credenciales(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.CampoInvalido> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(this::aCampoInvalido)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Error de validacion", req, detalles);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodoNoSoportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generico(Exception ex, HttpServletRequest req) {
        // Trazar el detalle real en el log (la respuesta no expone internals).
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private ApiError.CampoInvalido aCampoInvalido(FieldError fe) {
        return new ApiError.CampoInvalido(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String mensaje,
                                           HttpServletRequest req, List<ApiError.CampoInvalido> detalles) {
        ApiError body = new ApiError(
                status.value(), status.getReasonPhrase(), mensaje,
                req.getRequestURI(), detalles, OffsetDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}
