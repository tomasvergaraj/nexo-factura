package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofPorTipo;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofTotales;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el ConsumoFolios (RCOF) firmado cumple el XSD OFICIAL del SII
 * ({@code ConsumoFolio_v10.xsd}), que hasta ahora era el unico XML tributario
 * del sistema sin esquema que lo respaldara: el modelo emitia Caratula y Resumen
 * colgando de la raiz, sin el envoltorio {@code DocumentoConsumoFolios} ni los
 * campos obligatorios de caratula, y nada lo detectaba.
 */
class RcofXmlGeneratorXsdTest {

    private static final LocalDate DIA = LocalDate.of(2026, 6, 26);
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-06-27T12:30:00Z"), ZoneId.of("America/Santiago"));

    private final DteXmlValidator validator = new DteXmlValidator(true);
    private final RcofXmlGenerator generator = new RcofXmlGenerator(RELOJ);
    private final FirmaElectronica firma = new FirmaElectronicaStub();

    @Test
    @DisplayName("el RCOF firmado cumple el XSD oficial ConsumoFolio_v10")
    void rcofFirmadoEsValido() {
        String xml = firmado(reporte());

        assertThatCode(() -> validator.validarConsumoFolios(xml)).doesNotThrowAnyException();
        // El upload del SII identifica el archivo por el schemaLocation: sin el
        // rechaza con "SCH-00001: Invalid Schema Name" antes de validar nada.
        assertThat(xml).contains(
                "xsi:schemaLocation=\"http://www.sii.cl/SiiDte ConsumoFolio_v10.xsd\"");
        assertThat(xml).contains("<TipoDocumento>39</TipoDocumento>")
                .contains("<TipoDocumento>41</TipoDocumento>")
                .contains("<RangoUtilizados>")
                .contains("<RangoAnulados>");
    }

    @Test
    @DisplayName("la caratula lleva los campos que el esquema exige, no solo los del reporte")
    void caratulaCompleta() {
        String xml = firmado(reporte());

        // El envoltorio con ID: es el destino de la Reference de la firma.
        assertThat(xml).contains("<DocumentoConsumoFolios ID=\"" + RcofXmlGenerator.ID_CONSUMO_FOLIOS + "\">");
        assertThat(xml).contains("<RutEmisor>76543210-9</RutEmisor>")
                .contains("<RutEnvia>11111111-1</RutEnvia>")   // firmante, no emisor
                .contains("<FchResol>2014-08-22</FchResol>")
                .contains("<NroResol>80</NroResol>")
                .contains("<FchInicio>2026-06-26</FchInicio>")
                .contains("<FchFinal>2026-06-26</FchFinal>")
                .contains("<SecEnvio>3</SecEnvio>")
                .contains("<TmstFirmaEnv>2026-06-27T08:30:00</TmstFirmaEnv>");
    }

    @Test
    @DisplayName("sin firma el RCOF NO cumple el esquema: la validacion va despues de firmar")
    void sinFirmaNoValida() {
        String sinFirmar = generator.generar(reporte(), emisor(), caratula(3));

        assertThatThrownBy(() -> validator.validarConsumoFolios(sinFirmar))
                .hasMessageContaining("ConsumoFolio_v10");
    }

    @Test
    @DisplayName("un tipo sin movimiento no entra al XML aunque el JSON lo lleve en cero")
    void tipoSinMovimientoNoSeEmite() {
        RcofResponse soloAfectas = new RcofResponse(DIA, 1,
                List.of(afectas(), sinMovimiento(41)),
                new RcofTotales(3, 2, 1, 30000, 5700, 0, 35700), false);

        String xml = firmado(soloAfectas);

        assertThatCode(() -> validator.validarConsumoFolios(xml)).doesNotThrowAnyException();
        assertThat(xml).contains("<TipoDocumento>39</TipoDocumento>")
                .doesNotContain("<TipoDocumento>41</TipoDocumento>");
    }

    // ---------- fixtures ----------

    private String firmado(RcofResponse reporte) {
        String xml = generator.generar(reporte, emisor(), caratula(reporte.secEnvio()));
        return firma.firmarEnveloped(xml, RcofXmlGenerator.ID_CONSUMO_FOLIOS, 1L);
    }

    private RcofXmlGenerator.CaratulaRcof caratula(int secEnvio) {
        return new RcofXmlGenerator.CaratulaRcof("11111111-1", "2014-08-22", 80, secEnvio);
    }

    private Empresa emisor() {
        return Empresa.builder()
                .rut("76543210-9")
                .razonSocial("Nexo Software SpA")
                .giro("Software")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build();
    }

    /** Dia con boletas afectas (2 utilizadas + 1 anulada) y una exenta. */
    private RcofResponse reporte() {
        return new RcofResponse(DIA, 3,
                List.of(afectas(),
                        new RcofPorTipo(41, 1, 1, 5L, 5L, 0, null, null, 0, 0, 8000, 8000)),
                new RcofTotales(4, 3, 1, 30000, 5700, 8000, 43700), false);
    }

    private RcofPorTipo afectas() {
        return new RcofPorTipo(39, 3, 2, 10L, 11L, 1, 12L, 12L, 30000, 5700, 0, 35700);
    }

    private RcofPorTipo sinMovimiento(int tipo) {
        return new RcofPorTipo(tipo, 0, 0, null, null, 0, null, null, 0, 0, 0, 0);
    }
}
