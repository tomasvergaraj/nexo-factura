package cl.nexosoftware.factura.tributario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stub de {@link SiiGateway} para desarrollo. Devuelve un TrackID sintetico y
 * simula la aceptacion del documento sin contactar al SII.
 */
@Component
@Profile("!prod")
@Slf4j
public class SiiGatewayStub implements SiiGateway {

    @Override
    public String enviar(String xmlDteFirmado) {
        long trackId = ThreadLocalRandom.current().nextLong(1_000_000, 9_999_999);
        log.info("Stub SII: envio simulado, TrackID={}", trackId);
        return String.valueOf(trackId);
    }

    @Override
    public EstadoEnvio consultarEstado(String trackId) {
        // En desarrollo asumimos aceptacion para poder recorrer el ciclo completo.
        return EstadoEnvio.ACEPTADO;
    }
}
