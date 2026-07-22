package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Set;

/**
 * Canal REST de boleta electronica (39/41), segun el spec OpenAPI oficial del
 * SII: envio multipart a pangal/rahue con Cookie TOKEN, consulta de estado por
 * {rut}-{dv}-{trackid} en apicert/api.
 *
 * Contrato de errores: problemas de transporte (conexion, timeout, 5xx) →
 * {@link SiiNoDisponibleException}, la senal de la contingencia; un 4xx de
 * negocio → error duro con el detalle. Un 401/"NO ESTA AUTENTICADO" invalida el
 * token cacheado y reintenta UNA vez.
 *
 * Mapeo de estados del envio (con 1 boleta por sobre, la estadistica decide
 * 1:1): REC/SOK/CRT/FOK/PRD → en proceso; EPR → segun estadistica
 * (rechazados/reparos/aceptados); RPR → aceptado con reparos;
 * RSC/RCH/RCO/RPT/RFR/VOF/RCT → rechazado.
 */
@Component
@Profile("prod")
@Slf4j
public class SiiTransporteBoleta extends SiiTransporteBase {

    private static final Set<Integer> TIPOS = Set.of(39, 41);
    private static final Set<String> EN_PROCESO = Set.of("REC", "SOK", "CRT", "FOK", "PRD", "PDR");
    private static final Set<String> RECHAZADOS = Set.of("RSC", "RCH", "RCO", "RPT", "RFR", "VOF", "RCT");

    private final RestClient http;
    private final EnvioBoletaGenerator envioGenerator;
    private final ObjectMapper json;
    private final SiiAmbiente ambiente;

    public SiiTransporteBoleta(SiiHttp siiHttp, SiiAuthClient auth,
                               EnvioBoletaGenerator envioGenerator, ObjectMapper json,
                               AppProperties props) {
        super(auth, "EnvioBOLETA");
        this.http = siiHttp.cliente();
        this.envioGenerator = envioGenerator;
        this.json = json;
        this.ambiente = SiiAmbiente.desde(props.sii().ambiente());
    }

    @Override
    public boolean soporta(int tipoDte) {
        return TIPOS.contains(tipoDte);
    }

    @Override
    public String enviar(SiiGateway.EnvioSii envio) {
        String sobre = envioGenerator.generar(envio);
        return conReintentoDeToken(token -> postEnvio(envio, sobre, token));
    }

    private String postEnvio(SiiGateway.EnvioSii envio, String sobre, String token) {
        MultipartUpload upload = multipartUpload(envio, sobre);
        String respuesta;
        try {
            respuesta = http.post()
                    .uri(ambiente.envioBoleta() + "/boleta.electronica.envio")
                    .header(HttpHeaders.CONTENT_TYPE, upload.contentType())
                    .header(HttpHeaders.COOKIE, "TOKEN=" + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(upload.cuerpo())
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new SiiNoDisponibleException("SII no disponible al enviar la boleta: " + e.getMessage());
        } catch (RestClientResponseException e) {
            throw traducirError(e, "enviar la boleta");
        } catch (RestClientException e) {
            // Conexion cortada LEYENDO la respuesta (no viene como
            // ResourceAccessException): transporte -> contingencia.
            throw new SiiNoDisponibleException("SII interrumpio la respuesta al enviar la boleta: " + e.getMessage());
        }

        RespuestaEnvio r = parsear(respuesta, RespuestaEnvio.class);
        if (r == null || r.trackid == null) {
            throw new SiiNoDisponibleException(
                    "El SII no entrego TrackID al recibir la boleta: " + resumen(respuesta));
        }
        log.info("Boleta T{}F{} recibida por el SII: TrackID={}, estado={}",
                envio.tipoDte(), envio.folio(), r.trackid, r.estado);
        return String.valueOf(r.trackid);
    }

    @Override
    public SiiGateway.EstadoEnvio consultarEstado(SiiGateway.ConsultaSii consulta) {
        return conReintentoDeToken(token -> getEstado(consulta, token));
    }

    private SiiGateway.EstadoEnvio getEstado(SiiGateway.ConsultaSii consulta, String token) {
        Rut emisor = Rut.de(consulta.rutEmisor());
        String respuesta;
        try {
            respuesta = http.get()
                    .uri(ambiente.apiBoleta() + "/boleta.electronica.envio/"
                            + emisor.numero() + "-" + emisor.dv() + "-" + consulta.trackId())
                    .header(HttpHeaders.COOKIE, "TOKEN=" + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new SiiNoDisponibleException("SII no disponible al consultar el estado: " + e.getMessage());
        } catch (RestClientResponseException e) {
            throw traducirError(e, "consultar el estado del envio " + consulta.trackId());
        } catch (RestClientException e) {
            throw new SiiNoDisponibleException(
                    "SII interrumpio la respuesta del estado del envio " + consulta.trackId() + ": " + e.getMessage());
        }

        RespuestaEstado r = parsear(respuesta, RespuestaEstado.class);
        if (r == null || r.estado == null) {
            throw new SiiNoDisponibleException(
                    "Respuesta de estado ilegible del SII para el TrackID " + consulta.trackId()
                            + ": " + resumen(respuesta));
        }
        return mapear(r, consulta.trackId());
    }

    /**
     * Reconciliacion por folio: el recurso por documento de la API de boleta
     * ({@code /boleta.electronica/{rut}-{dv}-{tipo}-{folio}/estado}). Un 404 es
     * la senal explicita de que el SII no conoce el folio (habilita reenviar);
     * un 200 significa que el folio YA esta registrado, y el estado decide.
     */
    @Override
    public SiiGateway.EstadoDocumento consultarDocumento(SiiGateway.ConsultaDocumento consulta) {
        return conReintentoDeToken(token -> getEstadoDocumento(consulta, token));
    }

    private SiiGateway.EstadoDocumento getEstadoDocumento(SiiGateway.ConsultaDocumento c, String token) {
        Rut emisor = Rut.de(c.rutEmisor());
        String id = emisor.numero() + "-" + emisor.dv() + "-" + c.tipoDte() + "-" + c.folio();
        String respuesta;
        try {
            respuesta = http.get()
                    .uri(ambiente.apiBoleta() + "/boleta.electronica/" + id + "/estado")
                    .header(HttpHeaders.COOKIE, "TOKEN=" + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new SiiNoDisponibleException(
                    "SII no disponible al reconciliar la boleta " + id + ": " + e.getMessage());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return SiiGateway.EstadoDocumento.NO_RECIBIDO;
            }
            if (e.getStatusCode().value() == 401 || e.getResponseBodyAsString().contains("NO ESTA AUTENTICADO")) {
                throw new TokenInvalidoSii();
            }
            // Cualquier otro codigo no determina el estado del documento: jamas
            // degradar a un falso NO_RECIBIDO (habilitaria duplicar el envio).
            throw new SiiNoDisponibleException("El SII respondio " + e.getStatusCode().value()
                    + " al reconciliar la boleta " + id + ": " + resumen(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new SiiNoDisponibleException(
                    "SII interrumpio la respuesta al reconciliar la boleta " + id + ": " + e.getMessage());
        }
        return mapearDocumento(parsear(respuesta, RespuestaDocumento.class), id);
    }

    /** Package-private: la matriz de mapeo se cubre directo en el test unitario. */
    static SiiGateway.EstadoDocumento mapearDocumento(RespuestaDocumento r, String id) {
        String estado = (r == null || r.estado == null) ? null : r.estado.trim().toUpperCase();
        if (estado == null) {
            // El recurso existe (200) pero sin estado legible: el folio esta en
            // el SII — lo seguro es no reenviar y esperar un estado concluyente.
            log.warn("La boleta {} existe en el SII pero la respuesta no trae estado; se trata como en proceso", id);
            return SiiGateway.EstadoDocumento.EN_PROCESO;
        }
        if (RECHAZADOS.contains(estado) || "RECHAZADO".equals(estado)) {
            return SiiGateway.EstadoDocumento.RECHAZADO;
        }
        if ("RPR".equals(estado) || "REPARO".equals(estado)) {
            return SiiGateway.EstadoDocumento.ACEPTADO_CON_REPARO;
        }
        if ("ACE".equals(estado) || "ACEPTADO".equals(estado) || "DOK".equals(estado)) {
            return SiiGateway.EstadoDocumento.ACEPTADO;
        }
        if (EN_PROCESO.contains(estado) || "EPR".equals(estado)) {
            // A nivel de documento no hay estadistica que desambigue un EPR:
            // se espera al estado final en vez de arriesgar un falso aceptado.
            return SiiGateway.EstadoDocumento.EN_PROCESO;
        }
        log.warn("Estado de documento desconocido del SII para la boleta {}: '{}' — no concluyente", id, estado);
        return SiiGateway.EstadoDocumento.DESCONOCIDO;
    }

    /** Package-private: la matriz de mapeo se cubre directo en el test unitario. */
    static SiiGateway.EstadoEnvio mapear(RespuestaEstado r, String trackId) {
        String estado = r.estado.trim().toUpperCase();
        if ("RPR".equals(estado)) {
            return SiiGateway.EstadoEnvio.ACEPTADO_CON_REPARO;
        }
        if (RECHAZADOS.contains(estado)) {
            log.warn("Envio {} rechazado por el SII (estado {}): {}", trackId, estado, r.detalle_rep_rech);
            return SiiGateway.EstadoEnvio.RECHAZADO;
        }
        if ("EPR".equals(estado)) {
            // EPR = procesado, pero NO implica aceptado: decide la estadistica.
            Estadistica e = r.estadistica != null && !r.estadistica.isEmpty() ? r.estadistica.get(0) : null;
            if (e == null) {
                return SiiGateway.EstadoEnvio.RECIBIDO; // procesando, sin desglose aun
            }
            if (e.rechazados > 0) {
                log.warn("Envio {} procesado con rechazo: {} / detalle: {}",
                        trackId, r.estadistica, r.detalle_rep_rech);
                return SiiGateway.EstadoEnvio.RECHAZADO;
            }
            if (e.reparos > 0) {
                return SiiGateway.EstadoEnvio.ACEPTADO_CON_REPARO;
            }
            if (e.aceptados > 0) {
                return SiiGateway.EstadoEnvio.ACEPTADO;
            }
            return SiiGateway.EstadoEnvio.RECIBIDO;
        }
        if (!EN_PROCESO.contains(estado)) {
            log.warn("Estado de envio desconocido del SII para {}: '{}' — se trata como en proceso", trackId, estado);
        }
        return SiiGateway.EstadoEnvio.RECIBIDO;
    }

    // ---------- soporte ----------

    private RuntimeException traducirError(RestClientResponseException e, String operacion) {
        String cuerpo = e.getResponseBodyAsString();
        if (e.getStatusCode().value() == 401 || cuerpo.contains("NO ESTA AUTENTICADO")) {
            return new TokenInvalidoSii();
        }
        if (e.getStatusCode().is5xxServerError()) {
            return new SiiNoDisponibleException(
                    "SII respondio " + e.getStatusCode().value() + " al " + operacion);
        }
        return new ReglaNegocioException(
                "El SII rechazo la peticion al " + operacion + " (" + e.getStatusCode().value()
                        + "): " + resumen(cuerpo));
    }

    private <T> T parsear(String cuerpo, Class<T> clase) {
        try {
            return json.readValue(cuerpo, clase);
        } catch (Exception e) {
            return null;
        }
    }

    private String resumen(String cuerpo) {
        if (cuerpo == null) return "(sin cuerpo)";
        String plano = cuerpo.replaceAll("\\s+", " ").trim();
        return plano.length() <= 300 ? plano : plano.substring(0, 300) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class RespuestaEnvio {
        public Long trackid;
        public String estado;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RespuestaDocumento {
        public String estado;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RespuestaEstado {
        public String estado;
        public List<Estadistica> estadistica;
        public Object detalle_rep_rech;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Estadistica {
        public int tipo;
        public int informados;
        public int aceptados;
        public int rechazados;
        public int reparos;

        @Override public String toString() {
            return "{tipo=" + tipo + ", informados=" + informados + ", aceptados=" + aceptados
                    + ", rechazados=" + rechazados + ", reparos=" + reparos + "}";
        }
    }
}
