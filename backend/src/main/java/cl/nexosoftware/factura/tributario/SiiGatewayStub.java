package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stub de {@link SiiGateway} para desarrollo. Devuelve un TrackID sintetico y
 * simula la respuesta del SII sin contactarlo.
 *
 * Su comportamiento es configurable en runtime (via {@link SiiStubController} o
 * las propiedades {@code app.sii.stub.*}) para poder ejercitar la contingencia
 * y el reenvio de rechazados de extremo a extremo:
 * <ul>
 *   <li>{@code disponible=false}: {@code enviar}/{@code consultarEstado} lanzan
 *       {@link SiiNoDisponibleException} (SII caido).</li>
 *   <li>{@code estadoConsulta}: resultado fijo de {@code consultarEstado}
 *       (ACEPTADO por defecto; RECHAZADO permite probar el reenvio).</li>
 * </ul>
 */
@Component
@Profile("!prod")
@Slf4j
public class SiiGatewayStub implements SiiGateway {

    private volatile boolean disponible;
    private volatile EstadoEnvio estadoConsulta;

    public SiiGatewayStub(
            @Value("${app.sii.stub.disponible:true}") boolean disponible,
            @Value("${app.sii.stub.estado-consulta:ACEPTADO}") EstadoEnvio estadoConsulta) {
        this.disponible = disponible;
        this.estadoConsulta = estadoConsulta;
    }

    @Override
    public String enviar(String xmlDteFirmado) {
        if (!disponible) {
            log.warn("Stub SII: envio rechazado, el SII esta simulado como NO disponible");
            throw new SiiNoDisponibleException("SII no disponible (simulado por el stub)");
        }
        long trackId = ThreadLocalRandom.current().nextLong(1_000_000, 9_999_999);
        log.info("Stub SII: envio simulado, TrackID={}", trackId);
        return String.valueOf(trackId);
    }

    @Override
    public EstadoEnvio consultarEstado(String trackId) {
        if (!disponible) {
            throw new SiiNoDisponibleException("SII no disponible (simulado por el stub)");
        }
        return estadoConsulta;
    }

    public boolean isDisponible() { return disponible; }
    public EstadoEnvio getEstadoConsulta() { return estadoConsulta; }

    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public void setEstadoConsulta(EstadoEnvio estadoConsulta) { this.estadoConsulta = estadoConsulta; }
}
