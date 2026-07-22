package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.tributario.SiiGateway.EstadoEnvio;
import cl.nexosoftware.factura.tributario.SiiTransporteBoleta.Estadistica;
import cl.nexosoftware.factura.tributario.SiiTransporteBoleta.RespuestaEstado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz de mapeo de estados de la API REST de boleta al enum del gateway.
 * Regla central: EPR ("procesado") NO implica aceptado — decide la estadistica
 * (con 1 boleta por envio el desglose mapea 1:1 al documento).
 */
class SiiTransporteBoletaTest {

    @ParameterizedTest(name = "{0} -> RECIBIDO (en proceso)")
    @ValueSource(strings = {"REC", "SOK", "CRT", "FOK", "PRD"})
    void estadosEnProceso(String estado) {
        assertThat(mapear(estado, null)).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @ParameterizedTest(name = "{0} -> RECHAZADO (final)")
    @ValueSource(strings = {"RSC", "RCH", "RCO", "RPT", "RFR", "VOF", "RCT"})
    void estadosRechazados(String estado) {
        assertThat(mapear(estado, null)).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @Test
    @DisplayName("RPR -> ACEPTADO_CON_REPARO")
    void rprEsReparo() {
        assertThat(mapear("RPR", null)).isEqualTo(EstadoEnvio.ACEPTADO_CON_REPARO);
    }

    @Test
    @DisplayName("EPR con la boleta aceptada -> ACEPTADO")
    void eprAceptado() {
        assertThat(mapear("EPR", estadistica(1, 0, 0))).isEqualTo(EstadoEnvio.ACEPTADO);
    }

    @Test
    @DisplayName("EPR con rechazo en la estadistica -> RECHAZADO (EPR no implica aceptado)")
    void eprConRechazo() {
        assertThat(mapear("EPR", estadistica(0, 1, 0))).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @Test
    @DisplayName("EPR con reparos -> ACEPTADO_CON_REPARO")
    void eprConReparos() {
        assertThat(mapear("EPR", estadistica(0, 0, 1))).isEqualTo(EstadoEnvio.ACEPTADO_CON_REPARO);
    }

    @Test
    @DisplayName("EPR sin estadistica aun -> RECIBIDO (sigue en proceso)")
    void eprSinEstadistica() {
        assertThat(mapear("EPR", null)).isEqualTo(EstadoEnvio.RECIBIDO);
        assertThat(mapear("EPR", List.of())).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @Test
    @DisplayName("EPR con estadistica en cero -> RECIBIDO")
    void eprEstadisticaEnCero() {
        assertThat(mapear("EPR", estadistica(0, 0, 0))).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    @Test
    @DisplayName("un estado desconocido se trata como en proceso (no rompe la consulta)")
    void estadoDesconocido() {
        assertThat(mapear("ZZZ", null)).isEqualTo(EstadoEnvio.RECIBIDO);
    }

    // ---------- matriz del documento por folio (reconciliacion) ----------

    @ParameterizedTest(name = "documento {0} -> RECHAZADO")
    @ValueSource(strings = {"RSC", "RCH", "RCO", "RPT", "RFR", "VOF", "RCT", "RECHAZADO"})
    void documentoRechazado(String estado) {
        assertThat(mapearDoc(estado)).isEqualTo(SiiGateway.EstadoDocumento.RECHAZADO);
    }

    @ParameterizedTest(name = "documento {0} -> ACEPTADO")
    @ValueSource(strings = {"ACE", "ACEPTADO", "DOK"})
    void documentoAceptado(String estado) {
        assertThat(mapearDoc(estado)).isEqualTo(SiiGateway.EstadoDocumento.ACEPTADO);
    }

    @ParameterizedTest(name = "documento {0} -> ACEPTADO_CON_REPARO")
    @ValueSource(strings = {"RPR", "REPARO"})
    void documentoConReparo(String estado) {
        assertThat(mapearDoc(estado)).isEqualTo(SiiGateway.EstadoDocumento.ACEPTADO_CON_REPARO);
    }

    @ParameterizedTest(name = "documento {0} -> EN_PROCESO (existe: no reenviar)")
    @ValueSource(strings = {"REC", "SOK", "CRT", "FOK", "PRD", "EPR"})
    void documentoEnProceso(String estado) {
        // A nivel de documento un EPR no trae estadistica que lo desambigue:
        // se espera el estado final en vez de arriesgar un falso aceptado.
        assertThat(mapearDoc(estado)).isEqualTo(SiiGateway.EstadoDocumento.EN_PROCESO);
    }

    @Test
    @DisplayName("documento con 200 pero sin estado legible -> EN_PROCESO (existe, jamas NO_RECIBIDO)")
    void documentoSinEstado() {
        assertThat(mapearDoc(null)).isEqualTo(SiiGateway.EstadoDocumento.EN_PROCESO);
        assertThat(SiiTransporteBoleta.mapearDocumento(null, "76543210-9-39-1"))
                .isEqualTo(SiiGateway.EstadoDocumento.EN_PROCESO);
    }

    @Test
    @DisplayName("documento con estado desconocido -> DESCONOCIDO (no concluyente: no reenviar)")
    void documentoEstadoDesconocido() {
        assertThat(mapearDoc("ZZZ")).isEqualTo(SiiGateway.EstadoDocumento.DESCONOCIDO);
    }

    // ---------- helpers ----------

    private EstadoEnvio mapear(String estado, List<Estadistica> estadistica) {
        RespuestaEstado r = new RespuestaEstado();
        r.estado = estado;
        r.estadistica = estadistica;
        return SiiTransporteBoleta.mapear(r, "123456");
    }

    private SiiGateway.EstadoDocumento mapearDoc(String estado) {
        SiiTransporteBoleta.RespuestaDocumento r = new SiiTransporteBoleta.RespuestaDocumento();
        r.estado = estado;
        return SiiTransporteBoleta.mapearDocumento(r, "76543210-9-39-1");
    }

    private List<Estadistica> estadistica(int aceptados, int rechazados, int reparos) {
        Estadistica e = new Estadistica();
        e.tipo = 39;
        e.informados = aceptados + rechazados + reparos;
        e.aceptados = aceptados;
        e.rechazados = rechazados;
        e.reparos = reparos;
        return List.of(e);
    }
}
