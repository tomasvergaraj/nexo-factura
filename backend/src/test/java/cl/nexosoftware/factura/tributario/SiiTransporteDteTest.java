package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.tributario.SiiGateway.EstadoEnvio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Matriz de mapeo de estados de QueryEstUp (canal clasico) al enum del gateway
 * — el espejo de {@link SiiTransporteBoletaTest}. Regla central: EPR
 * ("procesado") NO implica aceptado — deciden los conteos por documento; y los
 * estados 001-003 son token invalido (gatillan renovacion + reintento), no un
 * estado del envio.
 */
class SiiTransporteDteTest {

    @ParameterizedTest(name = "{0} -> RECIBIDO (en proceso)")
    @ValueSource(strings = {"REC", "SOK", "FOK", "PDR", "PRD", "CRT", "-11"})
    void estadosEnProceso(String estado) {
        assertThat(mapear(respuesta(estado))).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @ParameterizedTest(name = "{0} -> RECHAZADO (final)")
    @ValueSource(strings = {"RSC", "RCH", "RPT", "RFR", "VOF", "RCT"})
    void estadosRechazados(String estado) {
        assertThat(mapear(respuesta(estado))).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @ParameterizedTest(name = "{0} -> ACEPTADO_CON_REPARO")
    @ValueSource(strings = {"RPR", "RLV"})
    void estadosConReparo(String estado) {
        assertThat(mapear(respuesta(estado))).isEqualTo(EstadoEnvio.ACEPTADO_CON_REPARO);
    }

    @ParameterizedTest(name = "estado {0} -> token invalido (renueva y reintenta)")
    @ValueSource(strings = {"001", "002", "003"})
    void estadosDeTokenInvalido(String estado) {
        assertThatThrownBy(() -> mapear(respuesta(estado)))
                .isInstanceOf(TokenInvalidoSii.class);
    }

    @Test
    @DisplayName("EPR con el DTE aceptado -> ACEPTADO")
    void eprAceptado() {
        assertThat(mapear(respuestaEpr(1, 0, 0))).isEqualTo(EstadoEnvio.ACEPTADO);
    }

    @Test
    @DisplayName("EPR con rechazo por documento -> RECHAZADO (EPR no implica aceptado)")
    void eprConRechazo() {
        assertThat(mapear(respuestaEpr(0, 1, 0))).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @Test
    @DisplayName("EPR con reparos -> ACEPTADO_CON_REPARO")
    void eprConReparos() {
        assertThat(mapear(respuestaEpr(0, 0, 1))).isEqualTo(EstadoEnvio.ACEPTADO_CON_REPARO);
    }

    @Test
    @DisplayName("EPR con conteos en cero (o ilegibles) -> RECIBIDO (sigue en proceso)")
    void eprSinConteos() {
        assertThat(mapear(respuestaEpr(0, 0, 0))).isEqualTo(EstadoEnvio.RECIBIDO);
        assertThat(mapear(respuesta("EPR"))).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @Test
    @DisplayName("un estado desconocido se trata como en proceso (no rompe la consulta)")
    void estadoDesconocido() {
        assertThat(mapear(respuesta("ZZZ"))).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @Test
    @DisplayName("respuesta sin ESTADO -> SiiNoDisponibleException (ilegible, contingencia)")
    void respuestaIlegible() {
        assertThatThrownBy(() -> mapear("<SII:RESPUESTA xmlns:SII=\"http://www.sii.cl/XMLSchema\"/>"))
                .isInstanceOf(SiiNoDisponibleException.class);
    }

    // ---------- helpers ----------

    private EstadoEnvio mapear(String respuestaXml) {
        return SiiTransporteDte.mapearEstUp(respuestaXml, "123456");
    }

    /** Respuesta de QueryEstUp con solo el header (ESTADO + GLOSA). */
    private String respuesta(String estado) {
        return "<SII:RESPUESTA xmlns:SII=\"http://www.sii.cl/XMLSchema\">"
                + "<SII:RESP_HDR><ESTADO>" + estado + "</ESTADO><GLOSA>glosa</GLOSA></SII:RESP_HDR>"
                + "</SII:RESPUESTA>";
    }

    /** Respuesta EPR con el desglose de documentos del body. */
    private String respuestaEpr(int aceptados, int rechazados, int reparos) {
        return "<SII:RESPUESTA xmlns:SII=\"http://www.sii.cl/XMLSchema\">"
                + "<SII:RESP_HDR><ESTADO>EPR</ESTADO></SII:RESP_HDR>"
                + "<SII:RESP_BODY>"
                + "<ACEPTADOS>" + aceptados + "</ACEPTADOS>"
                + "<RECHAZADOS>" + rechazados + "</RECHAZADOS>"
                + "<REPAROS>" + reparos + "</REPAROS>"
                + "</SII:RESP_BODY>"
                + "</SII:RESPUESTA>";
    }
}
