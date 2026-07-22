package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.tributario.SiiGateway.EstadoDocumento;
import cl.nexosoftware.factura.tributario.SiiGateway.EstadoEnvio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    private final SiiGatewayStub stub;

    public record EstadoStub(Boolean disponible, EstadoEnvio estadoConsulta,
                             EstadoDocumento estadoDocumento) {}

    @GetMapping
    @Operation(summary = "Estado actual del simulador del SII")
    public EstadoStub estado() {
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
