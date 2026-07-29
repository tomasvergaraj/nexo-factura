package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.tributario.SiiGateway.EstadoDocumento;
import cl.nexosoftware.factura.tributario.SiiGateway.EstadoEnvio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint SOLO de desarrollo (perfil != prod) para manipular el stub del SII y
 * asi ejercitar la contingencia y el reenvio sin depender del servicio real:
 * simular la caida del SII (disponible=false) o forzar el resultado de la
 * consulta de estado (RECHAZADO para probar el reenvio de rechazados).
 */
@RestController
@RequestMapping("/api/dev/sii-stub")
@RequiredArgsConstructor
@Profile("!prod")
@Tag(name = "Dev: stub SII", description = "Control del simulador del SII (solo desarrollo)")
public class SiiStubController {

    /**
     * Se resuelve PEREZOSAMENTE a proposito, en vez de inyectar el
     * {@link SiiGatewayStub} por constructor.
     *
     * Un test de integracion que sustituye el gateway con {@code @MockBean SiiGateway}
     * reemplaza el bean por un mock de la INTERFAZ: el tipo concreto desaparece del
     * contexto y este controller —que no tiene nada que ver con lo que ese test
     * ejercita— tumbaba el contexto ENTERO con un
     * "No qualifying bean of type SiiGatewayStub". Como Spring no reintenta un
     * contexto que ya fallo, se caian en cascada todas las clases que comparten esa
     * firma. Con {@code ObjectProvider} el controller siempre se construye y solo
     * falla —de forma explicita— quien de verdad llama al endpoint sin stub detras.
     */
    private final ObjectProvider<SiiGatewayStub> stubProvider;

    public record EstadoStub(Boolean disponible, EstadoEnvio estadoConsulta,
                             EstadoDocumento estadoDocumento) {}

    private SiiGatewayStub stub() {
        SiiGatewayStub stub = stubProvider.getIfAvailable();
        if (stub == null) {
            throw new ReglaNegocioException(
                    "No hay un simulador del SII activo: este endpoint solo opera con SiiGatewayStub");
        }
        return stub;
    }

    @GetMapping
    @Operation(summary = "Estado actual del simulador del SII")
    public EstadoStub estado() {
        SiiGatewayStub stub = stub();
        return new EstadoStub(stub.isDisponible(), stub.getEstadoConsulta(), stub.getEstadoDocumento());
    }

    @PutMapping
    @Operation(summary = "Configurar el simulador: disponible=false simula la caida del SII; "
            + "estadoConsulta=RECHAZADO hace que la consulta de estado rechace los envios; "
            + "estadoDocumento controla la reconciliacion por folio previa al reenvio "
            + "(NO_RECIBIDO deja reenviar; ACEPTADO simula un folio que el SII ya tenia).")
    // El estado del stub es GLOBAL al proceso (afecta a todas las empresas de un
    // ambiente compartido): solo un ADMIN puede mutarlo.
    @PreAuthorize("hasRole('ADMIN')")
    public EstadoStub configurar(@RequestBody EstadoStub req) {
        SiiGatewayStub stub = stub();
        if (req.disponible() != null) {
            stub.setDisponible(req.disponible());
        }
        if (req.estadoConsulta() != null) {
            stub.setEstadoConsulta(req.estadoConsulta());
        }
        if (req.estadoDocumento() != null) {
            stub.setEstadoDocumento(req.estadoDocumento());
        }
        return estado();
    }
}
