package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.auth.RateLimiter;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta publica de boletas ({@code /api/public/boletas/pdf}): el sitio de
 * verificacion que el Formato de Boletas Electronicas del SII exige al emisor.
 *
 * <p>Contrato bajo prueba: SIN token, la boleta se entrega solo con la
 * coincidencia EXACTA de los cinco datos impresos; cualquier diferencia (o una
 * empresa sin sitio configurado, o un tipo que no es boleta) responde el mismo
 * 404 sin revelar cual campo fallo; y pasada la ventana de tres meses el
 * documento deja de estar disponible con un mensaje explicito.
 */
@AutoConfigureMockMvc
class ConsultaBoletaPublicaIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private RateLimiter rateLimiter;

    private String rutConSitio;
    private String rutSinSitio;

    @BeforeEach
    void preparar() {
        // Los 404 deliberados de estos tests consumen presupuesto del rate
        // limiter por IP (compartido con login); se parte limpio para no
        // acumular bloqueos entre tests ni contaminar otras clases.
        rateLimiter.reset();

        rutConSitio = rutUnicoDeTest();
        Empresa conSitio = empresaRepository.save(Empresa.builder()
                .rut(rutConSitio)
                .razonSocial("Emisor Con Sitio")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .urlConsultaBoleta("consultaboleta.test.cl")
                .build());

        rutSinSitio = rutUnicoDeTest();
        Empresa sinSitio = empresaRepository.save(Empresa.builder()
                .rut(rutSinSitio)
                .razonSocial("Emisor Sin Sitio")
                .giro("Pruebas")
                .direccion("Calle 2")
                .comuna("Quillota")
                .build());

        documentoRepository.save(boletaEmitida(conSitio.getId(), 156L, LocalDate.now(), 29800L));
        // Fuera de la ventana de 3 meses del formato del SII.
        documentoRepository.save(boletaEmitida(conSitio.getId(), 90L, LocalDate.now().minusMonths(4), 5000L));
        // La empresa sin sitio tambien tiene una boleta real: ni asi se expone.
        documentoRepository.save(boletaEmitida(sinSitio.getId(), 156L, LocalDate.now(), 29800L));
    }

    @Test
    @DisplayName("con los cinco datos exactos y sin token, entrega el PDF")
    void coincidenciaExactaEntregaPdf() throws Exception {
        mockMvc.perform(get("/api/public/boletas/pdf")
                        .param("rutEmisor", rutConSitio)
                        .param("tipo", "39")
                        .param("folio", "156")
                        .param("fecha", LocalDate.now().toString())
                        .param("total", "29800"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("cualquier dato distinto responde el mismo 404, sin revelar cual fallo")
    void datoDistintoRespondeMismo404() throws Exception {
        String hoy = LocalDate.now().toString();
        // total equivocado, folio inexistente, fecha corrida y tipo no-boleta (33):
        // cuatro fallos distintos, un unico mensaje.
        for (String[] params : new String[][]{
                {rutConSitio, "39", "156", hoy, "29801"},
                {rutConSitio, "39", "999", hoy, "29800"},
                {rutConSitio, "39", "156", LocalDate.now().minusDays(1).toString(), "29800"},
                {rutConSitio, "33", "156", hoy, "29800"},
        }) {
            mockMvc.perform(get("/api/public/boletas/pdf")
                            .param("rutEmisor", params[0])
                            .param("tipo", params[1])
                            .param("folio", params[2])
                            .param("fecha", params[3])
                            .param("total", params[4]))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje", containsString("Boleta no encontrada")));
        }
    }

    @Test
    @DisplayName("una empresa sin sitio configurado no expone sus boletas ni con datos exactos")
    void empresaSinSitioNoExpone() throws Exception {
        mockMvc.perform(get("/api/public/boletas/pdf")
                        .param("rutEmisor", rutSinSitio)
                        .param("tipo", "39")
                        .param("folio", "156")
                        .param("fecha", LocalDate.now().toString())
                        .param("total", "29800"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", containsString("Boleta no encontrada")));
    }

    @Test
    @DisplayName("pasados los 3 meses del formato, la boleta deja de estar disponible")
    void fueraDeVentanaNoDisponible() throws Exception {
        mockMvc.perform(get("/api/public/boletas/pdf")
                        .param("rutEmisor", rutConSitio)
                        .param("tipo", "39")
                        .param("folio", "90")
                        .param("fecha", LocalDate.now().minusMonths(4).toString())
                        .param("total", "5000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", containsString("3 meses")));
    }

    /** Boleta emitida: con folio, XML firmado (con TED) y montos coherentes. */
    private DocumentoTributario boletaEmitida(Long empresaId, Long folio, LocalDate fecha, long total) {
        long neto = Math.round(total / 1.19);
        DocumentoTributario doc = DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.BOLETA_AFECTA)
                .estado(EstadoDte.ACEPTADO)
                .folio(folio)
                .fechaEmision(fecha)
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(neto)
                .exento(0)
                .tasaIva(19.0)
                .iva(total - neto)
                .total(total)
                .xmlDte("<DTE><Documento><TED version=\"1.0\"><DD><RE>" + rutConSitio
                        + "</RE><F>" + folio + "</F></DD></TED></Documento></DTE>")
                .build();
        doc.agregarLinea(linea(total));
        return doc;
    }

    private LineaDetalle linea(long total) {
        LineaDetalle l = new LineaDetalle();
        l.setNombre("Item de prueba");
        l.setCantidad(1.0);
        l.setUnidad("UN");
        l.setPrecioUnitario(total);
        l.setAfecto(true);
        l.setMontoLinea(total);
        return l;
    }
}
