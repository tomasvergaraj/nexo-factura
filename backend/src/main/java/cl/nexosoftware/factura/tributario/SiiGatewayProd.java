package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gateway real del SII (perfil prod): rutea cada operacion al transporte del
 * tipo de documento — boletas 39/41 via {@link SiiTransporteBoleta} (API REST,
 * apicert/pangal) y facturas/notas 33/34/56/61 via {@link SiiTransporteDte}
 * (canal clasico, maullin/palena). Canales con hosts, autenticacion y sobres
 * independientes.
 *
 * Contrato de errores (el que espera DocumentoService): errores de transporte →
 * {@link cl.nexosoftware.factura.common.exception.SiiNoDisponibleException}
 * (deja el DTE EN_CONTINGENCIA); rechazos de negocio → excepcion dura.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SiiGatewayProd implements SiiGateway {

    private final List<SiiTransporte> transportes;

    @PostConstruct
    void avisar() {
        log.info("SiiGatewayProd activo con {} transporte(s): {}", transportes.size(),
                transportes.stream().map(t -> t.getClass().getSimpleName()).toList());
    }

    @Override
    public String enviar(EnvioSii envio) {
        return transporte(envio.tipoDte()).enviar(envio);
    }

    @Override
    public EstadoEnvio consultarEstado(ConsultaSii consulta) {
        return transporte(consulta.tipoDte()).consultarEstado(consulta);
    }

    private SiiTransporte transporte(int tipoDte) {
        return transportes.stream()
                .filter(t -> t.soporta(tipoDte))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(
                        "Ningun canal del SII soporta el tipo de documento " + tipoDte));
    }
}
