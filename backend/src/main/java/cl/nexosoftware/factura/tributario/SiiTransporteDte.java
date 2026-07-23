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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Canal CLASICO de DTE (facturas/notas 33/34/56/61): upload multipart del
 * sobre EnvioDTE a {@code cgi_dte/UPL/DTEUpload} (maullin cert / palena prod)
 * con Cookie TOKEN, consulta de estado del envio por {@code QueryEstUp.jws} y
 * consulta del documento por folio por {@code QueryEstDte.jws} (ambas SOAP).
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
    // Estados finales de rechazo del envio clasico (QueryEstUp). LRH/LRF/LRS:
    // rechazos de LIBRO IECV (contenido, firma, esquema); LNC: el tipo de envio
    // no corresponde (p.ej. TOTAL sobre un periodo ya cerrado/cuadrado); LRC:
    // caratula invalida (p.ej. periodo tributario futuro).
    private static final Set<String> RECHAZADOS = Set.of("RSC", "RCH", "RPT", "RFR", "VOF", "RCT",
            "LRH", "LRF", "LRS", "LNC", "LRC");
    // Aceptado con reparos (graves/leves) a nivel de envio.
    private static final Set<String> REPAROS = Set.of("RPR", "RLV");
    private static final Set<String> EN_PROCESO = Set.of("REC", "SOK", "FOK", "PDR", "PRD", "CRT", "-11");
    private static final Set<String> TOKEN_INVALIDO = Set.of("001", "002", "003");
    // getEstDte exige la fecha de emision como ddMMyyyy.
    private static final DateTimeFormatter FECHA_GETESTDTE = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final RestClient http;
    private final SiiSoap soap;
    private final EnvioDteGenerator envioGenerator;
    private final CertificadoResolver certificadoResolver;
    private final SiiAmbiente ambiente;

    public SiiTransporteDte(SiiHttp siiHttp, SiiSoap soap, SiiDteAuthClient auth,
                            EnvioDteGenerator envioGenerator, CertificadoResolver certificadoResolver,
                            AppProperties props) {
        super(auth, "EnvioDTE");
        this.http = siiHttp.cliente();
        this.soap = soap;
        this.envioGenerator = envioGenerator;
        this.certificadoResolver = certificadoResolver;
        this.ambiente = SiiAmbiente.desde(props.sii().ambiente());
    }

    @Override
    public boolean soporta(int tipoDte) {
        return TIPOS.contains(tipoDte);
    }

    @Override
    public String enviar(SiiGateway.EnvioSii envio) {
        String sobre = envioGenerator.generar(envio);
        return conReintentoDeToken(envio.empresaId(), token -> upload(envio, sobre, token));
    }

    /**
     * Sube el libro IECV firmado por el MISMO upload del canal clasico: el
     * DTEUpload acepta tanto sobres EnvioDTE como LibroCompraVenta, con la misma
     * semantica de STATUS/TRACKID; el estado posterior se consulta por QueryEstUp.
     */
    @Override
    public String enviarLibro(SiiGateway.EnvioLibroSii envio) {
        String nombre = "LibroCV_" + envio.tipoOperacion() + "_" + envio.periodo() + ".xml";
        return conReintentoDeToken(envio.empresaId(), token -> subirSobre(
                envio.rutEmisor(), nombre, envio.xmlFirmado(), token,
                "el libro IECV " + envio.tipoOperacion() + " " + envio.periodo()));
    }

    /** Sobre multi-documento (set de pruebas): un EnvioDTE con N DTEs, un TrackID. */
    @Override
    public String enviarLote(java.util.List<SiiGateway.EnvioSii> envios) {
        String sobre = envioGenerator.generarLote(envios);
        String rutEmisor = envios.get(0).rutEmisor();
        String nombre = "EnvioDTE_LOTE_" + envios.size() + "docs.xml";
        return conReintentoDeToken(envios.get(0).empresaId(), token -> subirSobre(
                rutEmisor, nombre, sobre, token, "el lote EnvioDTE de " + envios.size() + " documentos"));
    }

    private String upload(SiiGateway.EnvioSii envio, String sobre, String token) {
        return subirSobre(envio.rutEmisor(),
                "EnvioDTE_T" + envio.tipoDte() + "F" + envio.folio() + ".xml",
                sobre, token, "el EnvioDTE T" + envio.tipoDte() + "F" + envio.folio());
    }

    private String subirSobre(String rutEmisor, String nombreArchivo, String sobre,
                              String token, String contexto) {
        MultipartUpload subida = multipartUpload(rutEmisor, nombreArchivo, sobre);
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
            throw new SiiNoDisponibleException("SII no disponible al subir " + contexto + ": " + e.getMessage());
        } catch (RestClientResponseException e) {
            // Mismo contrato que el canal de boleta: 401/403 = token, 5xx =
            // no disponible, otro 4xx = rechazo duro.
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                throw new TokenInvalidoSii();
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new SiiNoDisponibleException("SII respondio " + codigo + " al subir " + contexto);
            }
            throw new ReglaNegocioException(
                    "El SII rechazo el upload de " + contexto + " (HTTP " + codigo + ")");
        } catch (RestClientException e) {
            // Conexion cortada LEYENDO la respuesta (no viene como
            // ResourceAccessException): transporte -> contingencia.
            throw new SiiNoDisponibleException("SII interrumpio la respuesta al subir " + contexto + ": " + e.getMessage());
        }

        String status = soap.textoElemento(respuesta, "STATUS");
        String trackId = soap.textoElemento(respuesta, "TRACKID");
        if ("0".equals(status) && trackId != null && !trackId.isBlank()) {
            log.info("Upload de {} recibido por el SII: TrackID={}", contexto, trackId);
            return trackId.trim();
        }
        if ("5".equals(status)) {
            throw new TokenInvalidoSii();
        }
        if ("9".equals(status)) {
            // Bloqueo transitorio del lado del SII: contingencia, no rechazo.
            throw new SiiNoDisponibleException("El sistema del SII esta bloqueado (STATUS=9)");
        }
        if ("99".equals(status)) {
            // Sobre duplicado: un intento previo SI llego aunque su respuesta se
            // perdio. No es un rechazo del documento — el documento queda en
            // contingencia y la reconciliacion por folio adoptara el estado
            // cuando el SII termine de procesar el primer sobre.
            throw new SiiNoDisponibleException(
                    "El SII ya habia recibido este envio antes (STATUS=99); el documento queda en "
                            + "contingencia y la reconciliacion por folio adoptara su estado al reintentarlo");
        }
        String detalle = soap.textoElemento(respuesta, "DETAIL");
        throw new ReglaNegocioException("El SII rechazo el upload de " + contexto + " (STATUS="
                + status + "): " + glosaStatus(status) + (detalle != null ? " — " + detalle.trim() : ""));
    }

    /**
     * Reconciliacion por folio via {@code QueryEstDte.jws} (getEstDte): el SII
     * responde si un documento con ese folio/receptor/fecha/monto esta
     * registrado. El RutConsultante es el firmante del certificado (el mismo
     * autorizado que envia). Fecha en formato ddMMyyyy, como exige el servicio.
     */
    @Override
    public SiiGateway.EstadoDocumento consultarDocumento(SiiGateway.ConsultaDocumento consulta) {
        return conReintentoDeToken(consulta.empresaId(), token -> getEstDte(consulta, token));
    }

    private SiiGateway.EstadoDocumento getEstDte(SiiGateway.ConsultaDocumento consulta, String token) {
        Rut consultante = Rut.de(certificadoResolver.paraEmpresa(consulta.empresaId()).rutFirmante());
        Rut emisor = Rut.de(consulta.rutEmisor());
        Rut receptor = Rut.de(consulta.rutReceptor());
        String respuesta = soap.invocar(ambiente.hostDte() + "/DTEWS/QueryEstDte.jws", "getEstDte",
                new String[]{"RutConsultante", consultante.numero()},
                new String[]{"DvConsultante", consultante.dv()},
                new String[]{"RutCompania", emisor.numero()},
                new String[]{"DvCompania", emisor.dv()},
                new String[]{"RutReceptor", receptor.numero()},
                new String[]{"DvReceptor", receptor.dv()},
                new String[]{"TipoDte", String.valueOf(consulta.tipoDte())},
                new String[]{"FolioDte", String.valueOf(consulta.folio())},
                new String[]{"FechaEmisionDte", consulta.fechaEmision().format(FECHA_GETESTDTE)},
                new String[]{"MontoDte", String.valueOf(consulta.monto())},
                new String[]{"Token", token});
        return mapearEstDte(respuesta, consulta.tipoDte(), consulta.folio());
    }

    /** Package-private: la matriz de mapeo se cubre directo en el test unitario. */
    static SiiGateway.EstadoDocumento mapearEstDte(String respuesta, int tipoDte, long folio) {
        String estado = SiiXml.textoElemento(respuesta, "ESTADO");
        if (estado == null) {
            throw new SiiNoDisponibleException(
                    "Respuesta ilegible del SII al consultar el documento T" + tipoDte + "F" + folio);
        }
        estado = estado.trim().toUpperCase();
        if (TOKEN_INVALIDO.contains(estado)) {
            throw new TokenInvalidoSii();
        }
        return switch (estado) {
            // Documento no recibido por el SII: el UNICO caso que habilita reenviar.
            case "FAU" -> SiiGateway.EstadoDocumento.NO_RECIBIDO;
            // Recibido y datos coinciden; o registrado con notas que lo
            // modifican/anulan (solo existen sobre un documento ya aceptado).
            case "DOK", "TMD", "TMC", "MMD", "MMC", "AND", "ANC" ->
                    SiiGateway.EstadoDocumento.ACEPTADO;
            // No autorizado (folio fuera de rango / anulado / empresa no
            // autorizada): reenviar el mismo XML jamas lo va a sanar.
            case "FNA", "FAN", "EMP" -> SiiGateway.EstadoDocumento.RECHAZADO;
            // Recibido pero los datos no coinciden con lo registrado: existe en
            // el SII (no reenviar) pero requiere revision manual.
            case "DNK" -> SiiGateway.EstadoDocumento.DESCONOCIDO;
            default -> {
                log.warn("Estado getEstDte desconocido para T{}F{}: '{}' (glosa: {}) — no concluyente",
                        tipoDte, folio, estado, SiiXml.textoElemento(respuesta, "GLOSA_ERR"));
                yield SiiGateway.EstadoDocumento.DESCONOCIDO;
            }
        };
    }

    @Override
    public SiiGateway.EstadoEnvio consultarEstado(SiiGateway.ConsultaSii consulta) {
        return conReintentoDeToken(consulta.empresaId(), token -> getEstUp(consulta, token));
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
        // LOK: libro IECV recibido y aceptado (QueryEstUp tambien responde libros).
        if ("LOK".equals(estado)) {
            return SiiGateway.EstadoEnvio.ACEPTADO;
        }
        if (REPAROS.contains(estado)) {
            return SiiGateway.EstadoEnvio.ACEPTADO_CON_REPARO;
        }
        if (RECHAZADOS.contains(estado)) {
            log.warn("Envio {} rechazado por el SII (estado {}, glosa: {})",
                    trackId, estado, SiiXml.textoElemento(respuesta, "GLOSA"));
            // Cuerpo completo: el detalle del rechazo (p.ej. la linea del error de
            // schema de un libro) viaja fuera de ESTADO/GLOSA.
            log.warn("Respuesta completa del SII para {}: {}", trackId, respuesta);
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
            // STATUS=9 (sistema bloqueado) y STATUS=99 (sobre duplicado) se
            // manejan antes como contingencia.
            default -> "error no catalogado";
        };
    }
}
