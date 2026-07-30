package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.tributario.LibroBoletaXmlGenerator.BoletaLibro;
import cl.nexosoftware.factura.tributario.LibroBoletaXmlGenerator.CaratulaLibroBoleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Libro de Boletas Electronicas contra el XSD OFICIAL {@code LibroBOLETA_v10}
 * (envio 4 del set de pruebas de boletas).
 *
 * Cubre las tres trampas del esquema: {@code TipoLibro} solo admite ESPECIAL,
 * {@code FolioNotificacion} es obligatorio, y los montos neto e IVA van SOLO en
 * el resumen del periodo.
 */
class LibroBoletaXmlGeneratorXsdTest {

    private static final YearMonth PERIODO = YearMonth.of(2026, 6);
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-01T12:30:00Z"), ZoneId.of("America/Santiago"));

    private final DteXmlValidator validator = new DteXmlValidator(true);
    private final LibroBoletaXmlGenerator generator = new LibroBoletaXmlGenerator(RELOJ);
    private final FirmaElectronica firma = new FirmaElectronicaStub();

    @Test
    @DisplayName("el libro firmado cumple el XSD oficial LibroBOLETA_v10")
    void libroFirmadoEsValido() {
        String xml = firmado(boletasDelSet());

        assertThatCode(() -> validator.validarLibroBoleta(xml)).doesNotThrowAnyException();
        assertThat(xml).contains("<LibroBoleta")
                .contains("<EnvioLibro ID=\"" + LibroBoletaXmlGenerator.ID_ENVIO_LIBRO_BOLETA + "\">")
                .contains("<TipoLibro>ESPECIAL</TipoLibro>")
                .contains("<TipoEnvio>TOTAL</TipoEnvio>")
                .contains("<FolioNotificacion>4965879</FolioNotificacion>")
                .contains("<PeriodoTributario>2026-06</PeriodoTributario>")
                .contains("<TmstFirma>2026-07-01T08:30:00</TmstFirma>");
    }

    @Test
    @DisplayName("un detalle por boleta, con TpoServ=3 y el folio anulado marcado con A")
    void detallePorBoleta() {
        String xml = firmado(boletasDelSet());

        assertThat(contar(xml, "<Detalle>")).isEqualTo(3);
        assertThat(xml).contains("<FolioDoc>156</FolioDoc>")
                .contains("<FolioDoc>157</FolioDoc>")
                .contains("<FolioDoc>158</FolioDoc>")
                .contains("<TpoServ>3</TpoServ>")
                .contains("<Anulado>A</Anulado>")
                .contains("<FchEmiDoc>2026-06-26</FchEmiDoc>");
    }

    @Test
    @DisplayName("el resumen del periodo lleva neto e IVA (obligatorios ahi) y no cuenta el anulado en los montos")
    void resumenDelPeriodo() {
        String xml = firmado(boletasDelSet());

        // 39: dos boletas (una anulada). Solo la vigente suma: 10000 + 1900 = 11900.
        assertThat(xml).contains("<TpoDoc>39</TpoDoc>")
                .contains("<TotDoc>2</TotDoc>")        // el anulado SI se cuenta como documento
                .contains("<TotAnulado>1</TotAnulado>")
                .contains("<TotMntNeto>10000</TotMntNeto>")
                .contains("<TotMntIVA>1900</TotMntIVA>")
                .contains("<TotMntTotal>11900</TotMntTotal>")
                .contains("<TasaIVA>19.0</TasaIVA>");
        // 41: exenta, sin IVA -> sin TasaIVA y con TotMntExe.
        assertThat(xml).contains("<TpoDoc>41</TpoDoc>")
                .contains("<TotMntExe>8000</TotMntExe>");
    }

    @Test
    @DisplayName("sin firma el libro NO cumple el esquema: la validacion va despues de firmar")
    void sinFirmaNoValida() {
        String sinFirmar = generator.generar(PERIODO, boletasDelSet(), emisor(), caratula());

        assertThatThrownBy(() -> validator.validarLibroBoleta(sinFirmar))
                .hasMessageContaining("LibroBOLETA_v10");
    }

    @Test
    @DisplayName("un tipo sin boletas no se informa: TotDoc es positiveInteger en el esquema")
    void tipoSinBoletasNoSeInforma() {
        String xml = firmado(List.of(afecta(156, false)));

        assertThatCode(() -> validator.validarLibroBoleta(xml)).doesNotThrowAnyException();
        assertThat(xml).contains("<TpoDoc>39</TpoDoc>").doesNotContain("<TpoDoc>41</TpoDoc>");
        assertThat(xml).doesNotContain("<TotAnulado>");   // sin anulados, se omite
    }

    // ---------- fixtures ----------

    private String firmado(List<BoletaLibro> boletas) {
        String xml = generator.generar(PERIODO, boletas, emisor(), caratula());
        return firma.firmarEnveloped(xml, LibroBoletaXmlGenerator.ID_ENVIO_LIBRO_BOLETA, 1L);
    }

    private CaratulaLibroBoleta caratula() {
        return new CaratulaLibroBoleta("11111111-1", "2014-08-22", 80, 4965879L);
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

    /** Dos afectas (una anulada) y una exenta. */
    private List<BoletaLibro> boletasDelSet() {
        return List.of(afecta(156, false), afecta(157, true),
                new BoletaLibro(41, 158, LocalDate.of(2026, 6, 26), false, 0, 0, 8000, 8000, 19.0));
    }

    private BoletaLibro afecta(long folio, boolean anulada) {
        return new BoletaLibro(39, folio, LocalDate.of(2026, 6, 26), anulada,
                10000, 1900, 0, 11900, 19.0);
    }

    private int contar(String xml, String fragmento) {
        return xml.split(java.util.regex.Pattern.quote(fragmento), -1).length - 1;
    }
}
