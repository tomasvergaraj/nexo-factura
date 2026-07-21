package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;

/**
 * Canal CLASICO de DTE (facturas/notas 33/34/56/61): upload multipart del
 * sobre EnvioDTE a {@code cgi_dte/UPL/DTEUpload} (maullin cert / palena prod)
 * con Cookie TOKEN, y consulta de estado por {@code QueryEstUp.jws} (SOAP).
 *
 * Contrato de errores identico al canal de boleta: transporte/5xx →
 * {@link SiiNoDisponibleException} (contingencia); rechazo de negocio → error
 * duro con el detalle. STATUS 5 del upload o estados 001-003 de la consulta =
 * token invalido → renovar y reintentar UNA vez.
 */
@Component
@Profile("prod")
@Slf4j
public class SiiTransporteDte extends SiiTransporteBase {

    private static final Set<Integer> TIPOS = Set.of(33, 34, 56, 61);
    // Estados finales de rechazo del envio clasico (QueryEstUp).
    private static final Set<String> RECHAZADOS = Set.of("RSC", "RCH", "RPT", "RFR", "VOF", "RCT");
    // Aceptado con reparos (graves/leves) a nivel de envio.
    private static final Set<String> REPAROS = Set.of("RPR", "RLV");
    private static final Set<String> EN_PROCESO = Set.of("REC", "SOK", "FOK", "PDR", "PRD", "CRT", "-11");
    private static final Set<String> TOKEN_INVALIDO = Set.of("001", "002", "003");

    private final RestClient http;
    private final SiiSoap soap;
    private final EnvioDteGenerator envioGenerator;
    private final SiiAmbiente ambiente;

    public SiiTransporteDte(SiiHttp siiHttp, SiiSoap soap, SiiDteAuthClient auth,
                            EnvioDteGenerator envioGenerator, AppProperties props) {
        super(auth, "EnvioDTE");
        this.http = siiHttp.cliente();
        this.soap = soap;
        this.envioGenerator = envioGenerator;
        this.ambiente = SiiAmbiente.desde(props.sii().ambiente());
    }

    @Override
    public boolean soporta(int tipoDte) {
        return TIPOS.contains(tipoDte);
    }

    @Override
    public String enviar(SiiGateway.EnvioSii envio) {
        String sobre = envioGenerator.generar(envio);
        return conReintentoDeToken(token -> upload(envio, sobre, token));
    }

    private String upload(SiiGateway.EnvioSii envio, String sobre, String token) {
        MultipartUpload subida = multipartUpload(envio, sobre);
        byte[] respuesta;
        try {
            respuesta = http.post()
                    .uri(ambiente.hostDte() + "/cgi_dte/UPL/DTEUpload")
                    .header(HttpHeaders.CONTENT_TYPE, subida.contentType())
                    .header(HttpHeaders.COOKIE, "TOKEN=" + token)
                    .body(subida.cuerpo())
                    .retrieve()
                    .body(byte[].class);
        } catch (ResourceAccessException e) {
            throw new SiiNoDisponibleException("SII no disponible al subir el EnvioDTE: " + e.getMessage());
        } catch (RestClientResponseException e) {
            // Mismo contrato que el canal de boleta: 401/403 = token, 5xx =
            // no disponible, otro 4xx = rechazo duro.
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                throw new TokenInvalidoSii();
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new SiiNoDisponibleException("SII respondio " + codigo + " al subir el EnvioDTE");
            }
            throw new ReglaNegocioException(
                    "El SII rechazo el upload del EnvioDTE (HTTP " + codigo + ")");
        }

        String status = soap.textoElemento(respuesta, "STATUS");
        String trackId = soap.textoElemento(respuesta, "TRACKID");
        if ("0".equals(status) && trackId != null && !trackId.isBlank()) {
            log.info("EnvioDTE T{}F{} recibido por el SII: TrackID={}", envio.tipoDte(), envio.folio(), trackId);
            return trackId.trim();
        }
        if ("5".equals(status)) {
            throw new TokenInvalidoSii();
        }
        if ("9".equals(status)) {
            // Bloqueo transitorio del lado del SII: contingencia, no rechazo.
            throw new SiiNoDisponibleException("El sistema del SII esta bloqueado (STATUS=9)");
        }
        String detalle = soap.textoElemento(respuesta, "DETAIL");
        throw new ReglaNegocioException("El SII rechazo el upload del EnvioDTE (STATUS="
                + status + "): " + glosaStatus(status) + (detalle != null ? " — " + detalle.trim() : ""));
    }

    @Override
    public SiiGateway.EstadoEnvio consultarEstado(SiiGateway.ConsultaSii consulta) {
        return conReintentoDeToken(token -> getEstUp(consulta, token));
    }

    private SiiGateway.EstadoEnvio getEstUp(SiiGateway.ConsultaSii consulta, String token) {
        Rut emisor = Rut.de(consulta.rutEmisor());
        String respuesta = soap.invocar(ambiente.hostDte() + "/DTEWS/QueryEstUp.jws", "getEstUp",
                new String[]{"Rut", emisor.numero()},
                new String[]{"Dv", emisor.dv()},
                new String[]{"TrackId", consulta.trackId()},
                new String[]{"Token", token});
        return mapearEstUp(respuesta, consulta.trackId());
    }

    /** Package-private: la matriz de mapeo se cubre directo en el test unitario. */
    static SiiGateway.EstadoEnvio mapearEstUp(String respuesta, String trackId) {
        String estado = SiiXml.textoElemento(respuesta, "ESTADO");
        if (estado == null) {
            throw new SiiNoDisponibleException(
                    "Respuesta de estado ilegible del SII para el TrackID " + trackId);
        }
        estado = estado.trim().toUpperCase();

        if (TOKEN_INVALIDO.contains(estado)) {
            throw new TokenInvalidoSii();
        }
        if (REPAROS.contains(estado)) {
            return SiiGateway.EstadoEnvio.ACEPTADO_CON_REPARO;
        }
        if (RECHAZADOS.contains(estado)) {
            log.warn("Envio {} rechazado por el SII (estado {}, glosa: {})",
                    trackId, estado, SiiXml.textoElemento(respuesta, "GLOSA"));
            return SiiGateway.EstadoEnvio.RECHAZADO;
        }
        if ("EPR".equals(estado)) {
            // Procesado: deciden los conteos por documento (con 1 DTE por envio, 1:1).
            int rechazados = contar(respuesta, "RECHAZADOS");
            int reparos = contar(respuesta, "REPAROS");
            int aceptados = contar(respuesta, "ACEPTADOS");
            if (rechazados > 0) {
                log.warn("Envio {} procesado con rechazo a nivel de documento", trackId);
                return SiiGateway.EstadoEnvio.RECHAZADO;
            }
            if (reparos > 0) {
                return SiiGateway.EstadoEnvio.ACEPTADO_CON_REPARO;
            }
            return aceptados > 0 ? SiiGateway.EstadoEnvio.ACEPTADO : SiiGateway.EstadoEnvio.RECIBIDO;
        }
        if (!EN_PROCESO.contains(estado)) {
            log.warn("Estado clasico desconocido del SII para {}: '{}' — se trata como en proceso",
                    trackId, estado);
        }
        return SiiGateway.EstadoEnvio.RECIBIDO;
    }

    // ---------- soporte ----------

    private static int contar(String respuesta, String elemento) {
        String valor = SiiXml.textoElemento(respuesta, elemento);
        try {
            return valor == null ? 0 : Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String glosaStatus(String status) {
        return switch (status == null ? "" : status) {
            case "1" -> "el firmante no tiene permiso para enviar por esta empresa";
            case "2", "3" -> "error en el tamano o corte del archivo";
            case "6" -> "la empresa no esta autorizada a enviar en este ambiente";
            case "7" -> "el sobre no cumple el schema del SII";
            case "8" -> "error en la firma del documento";
            // STATUS=9 (sistema bloqueado) se maneja antes como contingencia.
            case "99" -> "este envio ya fue recibido antes por el SII (un intento previo llego "
                    + "aunque su respuesta se perdio); concilie el TrackID en el portal del SII "
                    + "antes de volver a enviar";
            default -> "error no catalogado";
        };
    }
}
