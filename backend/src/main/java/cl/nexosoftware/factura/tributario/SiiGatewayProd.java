package cl.nexosoftware.factura.tributario;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Esqueleto de {@link SiiGateway} para el perfil prod.
 *
 * El bean existe para que el contexto del perfil prod levante, pero la
 * integracion real con el SII todavia no esta implementada: obtener semilla,
 * firmar la semilla y solicitar token, armar el sobre EnvioDTE, subirlo al
 * endpoint del ambiente (certificacion/prod) y consultar el estado por TrackID.
 * Hasta entonces cada operacion falla de forma explicita en lugar de simular una
 * aceptacion como hace el stub de desarrollo.
 */
@Component
@Profile("prod")
@Slf4j
public class SiiGatewayProd implements SiiGateway {

    @PostConstruct
    void avisar() {
        log.warn("SiiGatewayProd activo: la integracion real con el SII esta PENDIENTE. "
                + "El envio y la consulta de estado fallaran hasta completarla.");
    }

    @Override
    public String enviar(String xmlDteFirmado) {
        throw new UnsupportedOperationException("Integracion real con el SII pendiente");
    }

    @Override
    public EstadoEnvio consultarEstado(String trackId) {
        throw new UnsupportedOperationException("Integracion real con el SII pendiente");
    }
}
