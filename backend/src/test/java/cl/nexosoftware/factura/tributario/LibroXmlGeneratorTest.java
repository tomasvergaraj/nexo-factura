package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.libro.LibroDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica que el XML LibroCompraVenta marshallado es bien formado y respeta la
 * estructura y el orden del subconjunto representativo del IECV.
 */
class LibroXmlGeneratorTest {

    private final LibroXmlGenerator generator = new LibroXmlGenerator();

    @Test
    @DisplayName("el libro de ventas marshalla Caratula, ResumenPeriodo y Detalle bien formados")
    void ventasBienFormado() {
        String xml = generator.generar(libroVentas(), emisor());

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        assertThatCode(() -> parsear(xml)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<LibroCompraVenta version=\"1.0\">")
                .contains("<RutEmisorLibro>91000000-0</RutEmisorLibro>")
                .contains("<PeriodoTributario>2026-07</PeriodoTributario>")
                .contains("<TipoOperacion>VENTA</TipoOperacion>")
                .contains("<TipoLibro>MENSUAL</TipoLibro>")
                .contains("<TipoEnvio>TOTAL</TipoEnvio>")
                .contains("<TpoDoc>33</TpoDoc>")
                .contains("<TotDoc>1</TotDoc>")
                .contains("<NroDoc>7</NroDoc>")
                .contains("<RUTDoc>77111222-3</RUTDoc>");
        // Orden del documento: Caratula < ResumenPeriodo < Detalle.
        assertThat(xml.indexOf("<Caratula>")).isLessThan(xml.indexOf("<ResumenPeriodo>"));
        assertThat(xml.indexOf("<ResumenPeriodo>")).isLessThan(xml.indexOf("<Detalle>"));
    }

    @Test
    @DisplayName("un documento anulado emite <Anulado>A</Anulado> y el resumen TotAnulado")
    void anuladoMarcado() {
        LibroResponse libro = new LibroResponse("2026-07", TipoOperacion.VENTA,
                List.of(new LibroResumenTipo(33, 1, 1, 100000, 0, 19000, 0, 0, 119000)),
                List.of(
                        detalle(33, 7, false, 100000, 19000, 119000),
                        detalle(33, 8, true, 0, 0, 0)),
                new LibroTotales(1, 1, 100000, 0, 19000, 0, 0, 119000), false);

        String xml = generator.generar(libro, emisor());

        assertThat(xml).contains("<Anulado>A</Anulado>").contains("<TotAnulado>1</TotAnulado>");
    }

    @Test
    @DisplayName("los opcionales en cero (exento, otros impuestos, retencion) se omiten")
    void opcionalesEnCeroSeOmiten() {
        String xml = generator.generar(libroVentas(), emisor());

        assertThat(xml)
                .doesNotContain("<MntExe>")
                .doesNotContain("<OtrosImp>")
                .doesNotContain("<IVARet>")
                .doesNotContain("<TotAnulado>");
    }

    @Test
    @DisplayName("un libro sin movimiento marshalla sin Detalle y bien formado")
    void sinMovimientoSinDetalle() {
        LibroResponse vacio = new LibroResponse("2026-07", TipoOperacion.COMPRA,
                List.of(), List.of(), new LibroTotales(0, 0, 0, 0, 0, 0, 0, 0), true);

        String xml = generator.generar(vacio, emisor());

        assertThatCode(() -> parsear(xml)).doesNotThrowAnyException();
        assertThat(xml).contains("<TipoOperacion>COMPRA</TipoOperacion>").doesNotContain("<Detalle>");
    }

    private static LibroResponse libroVentas() {
        return new LibroResponse("2026-07", TipoOperacion.VENTA,
                List.of(new LibroResumenTipo(33, 1, 0, 100000, 0, 19000, 0, 0, 119000)),
                List.of(detalle(33, 7, false, 100000, 19000, 119000)),
                new LibroTotales(1, 0, 100000, 0, 19000, 0, 0, 119000), false);
    }

    private static LibroDetalleDoc detalle(int tipo, long folio, boolean anulado,
                                           long neto, long iva, long total) {
        return new LibroDetalleDoc(tipo, folio, LocalDate.of(2026, 7, 15),
                "77111222-3", "Cliente de prueba", neto, 0, iva, 0, 0, total, anulado);
    }

    private static Empresa emisor() {
        return Empresa.builder()
                .rut("91000000-0")
                .razonSocial("Empresa Demo")
                .giro("Servicios")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build();
    }

    private static Document parsear(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
