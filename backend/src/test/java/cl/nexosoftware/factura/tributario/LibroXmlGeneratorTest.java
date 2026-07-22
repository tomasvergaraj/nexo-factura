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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el XML LibroCompraVenta cumple la estructura del esquema OFICIAL
 * LibroCV_v10: caratula completa, resumen con uso comun / no recuperable /
 * retencion, y que el libro FIRMADO (stub con forma valida) pasa el XSD.
 */
class LibroXmlGeneratorTest {

    private final LibroXmlGenerator generator = new LibroXmlGenerator();
    private final DteXmlValidator validator = new DteXmlValidator(true);

    private static final LibroXmlGenerator.CaratulaLibro CARATULA =
            new LibroXmlGenerator.CaratulaLibro("11111111-1", "2026-01-15", 0, "ESPECIAL", 4965880L);

    @Test
    @DisplayName("el libro de ventas marshalla la caratula oficial completa y bien formada")
    void ventasBienFormado() {
        String xml = generator.generar(libroVentas(), emisor(), CARATULA);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        assertThatCode(() -> parsear(xml)).doesNotThrowAnyException();
        assertThat(xml)
                // El upload del SII identifica el archivo por el schemaLocation:
                // sin el rechaza con STATUS=7 "Invalid Schema Name".
                .contains("<LibroCompraVenta version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"http://www.sii.cl/SiiDte LibroCV_v10.xsd\">")
                .contains("<EnvioLibro ID=\"" + LibroXmlGenerator.ID_ENVIO_LIBRO + "\">")
                .contains("<RutEmisorLibro>91000000-0</RutEmisorLibro>")
                .contains("<RutEnvia>11111111-1</RutEnvia>")
                .contains("<PeriodoTributario>2026-07</PeriodoTributario>")
                .contains("<FchResol>2026-01-15</FchResol>")
                .contains("<NroResol>0</NroResol>")
                .contains("<TipoOperacion>VENTA</TipoOperacion>")
                .contains("<TipoLibro>ESPECIAL</TipoLibro>")
                .contains("<TipoEnvio>TOTAL</TipoEnvio>")
                .contains("<FolioNotificacion>4965880</FolioNotificacion>")
                .contains("<TpoDoc>33</TpoDoc>")
                .contains("<TotDoc>1</TotDoc>")
                .contains("<NroDoc>7</NroDoc>")
                .contains("<RUTDoc>77111222-3</RUTDoc>")
                .contains("<TmstFirma>");
        // Orden del documento: Caratula < ResumenPeriodo < Detalle < TmstFirma.
        assertThat(xml.indexOf("<Caratula>")).isLessThan(xml.indexOf("<ResumenPeriodo>"));
        assertThat(xml.indexOf("<ResumenPeriodo>")).isLessThan(xml.indexOf("<Detalle>"));
        assertThat(xml.indexOf("<Detalle>")).isLessThan(xml.indexOf("<TmstFirma>"));
    }

    @Test
    @DisplayName("el libro de ventas FIRMADO (stub) cumple el esquema oficial LibroCV_v10")
    void ventasFirmadoCumpleXsd() {
        String xml = generator.generar(libroVentas(), emisor(), CARATULA);
        String firmado = new FirmaElectronicaStub().firmarEnveloped(xml, LibroXmlGenerator.ID_ENVIO_LIBRO);

        assertThatCode(() -> validator.validarLibro(firmado)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el libro de compras del set (uso comun, entrega gratuita, retencion 46) cumple el XSD")
    void comprasDelSetCumpleXsd() {
        // Espejo del LIBRO DE COMPRAS del set de certificacion: factura con IVA
        // uso comun (factor 0.60), entrega gratuita (cod 4) y retencion total (46).
        LibroResponse libro = new LibroResponse("2026-07", TipoOperacion.COMPRA,
                List.of(
                        new LibroResumenTipo(30, 2, 0, 50099, 0, 3862, 0, 0, 59618,
                                5657, 1, 3394, List.of()),
                        new LibroResumenTipo(33, 2, 0, 16339, 8790, 1212, 0, 0, 28234,
                                0, 0, 0, List.of(new IvaNoRecResumen(4, 1, 1893))),
                        new LibroResumenTipo(46, 1, 0, 9543, 0, 1813, 0, 1813, 9543,
                                0, 0, 0, List.of())),
                List.of(
                        det(30, 234, 20325, 0, 3862, 0, 24187, 0, 0, null),
                        det(30, 781, 29774, 0, 0, 0, 35431, 5657, 0, null),
                        det(33, 32, 6377, 8790, 1212, 0, 16379, 0, 0, null),
                        det(33, 67, 9962, 0, 0, 0, 11855, 0, 1893, 4),
                        det(46, 9, 9543, 0, 1813, 1813, 9543, 0, 0, null)),
                new LibroTotales(5, 0, 75981, 8790, 6887, 0, 1813, 97386), false, 0.60);

        String xml = generator.generar(libro, emisor(), CARATULA);
        String firmado = new FirmaElectronicaStub().firmarEnveloped(xml, LibroXmlGenerator.ID_ENVIO_LIBRO);

        assertThatCode(() -> validator.validarLibro(firmado)).doesNotThrowAnyException();
        assertThat(xml)
                // Uso comun: operaciones, monto, factor y credito en el resumen.
                .contains("<TotOpIVAUsoComun>1</TotOpIVAUsoComun>")
                .contains("<TotIVAUsoComun>5657</TotIVAUsoComun>")
                .contains("<FctProp>0.6</FctProp>")
                .contains("<TotCredIVAUsoComun>3394</TotCredIVAUsoComun>")
                .contains("<IVAUsoComun>5657</IVAUsoComun>")
                // Entrega gratuita: IVA no recuperable codigo 4.
                .contains("<CodIVANoRec>4</CodIVANoRec>")
                .contains("<MntIVANoRec>1893</MntIVANoRec>")
                .contains("<TotMntIVANoRec>1893</TotMntIVANoRec>")
                // Retencion total de la factura de compra: OtrosImp codigo 15.
                .contains("<CodImp>15</CodImp>")
                .contains("<TasaImp>19.0</TasaImp>")
                .contains("<MntImp>1813</MntImp>")
                .contains("<TotMntImp>1813</TotMntImp>")
                // Compras: TpoImp=1 (IVA) por detalle.
                .contains("<TpoImp>1</TpoImp>");
    }

    @Test
    @DisplayName("uso comun sin factor de proporcionalidad es un error claro")
    void usoComunSinFactorFalla() {
        LibroResponse libro = new LibroResponse("2026-07", TipoOperacion.COMPRA,
                List.of(new LibroResumenTipo(30, 1, 0, 29774, 0, 0, 0, 0, 35431,
                        5657, 1, 0, List.of())),
                List.of(det(30, 781, 29774, 0, 0, 0, 35431, 5657, 0, null)),
                new LibroTotales(1, 0, 29774, 0, 0, 0, 0, 35431), false, null);

        assertThatThrownBy(() -> generator.generar(libro, emisor(), CARATULA))
                .hasMessageContaining("factor de proporcionalidad");
    }

    @Test
    @DisplayName("los opcionales en cero (exento, otros impuestos, retencion) se omiten")
    void opcionalesEnCeroSeOmiten() {
        String xml = generator.generar(libroVentas(), emisor(), CARATULA);

        assertThat(xml)
                .doesNotContain("<MntExe>")
                .doesNotContain("<OtrosImp>")
                .doesNotContain("<IVARet")
                .doesNotContain("<TotAnulado>")
                .doesNotContain("<IVAUsoComun>")
                .doesNotContain("<IVANoRec>");
    }

    @Test
    @DisplayName("la retencion de cambio de sujeto en VENTAS va como IVARetTotal")
    void ventasRetencionComoIvaRetTotal() {
        LibroResponse libro = new LibroResponse("2026-07", TipoOperacion.VENTA,
                List.of(new LibroResumenTipo(33, 1, 0, 50000, 0, 9500, 0, 9500, 50000,
                        0, 0, 0, List.of())),
                List.of(det(33, 3, 50000, 0, 9500, 9500, 50000, 0, 0, null)),
                new LibroTotales(1, 0, 50000, 0, 9500, 0, 9500, 50000), false, null);

        String xml = generator.generar(libro, emisor(), CARATULA);

        assertThat(xml)
                .contains("<IVARetTotal>9500</IVARetTotal>")
                .contains("<TotIVARetTotal>9500</TotIVARetTotal>")
                .doesNotContain("<OtrosImp>");
    }

    @Test
    @DisplayName("un libro sin movimiento marshalla sin ResumenPeriodo ni Detalle y cumple el XSD")
    void sinMovimientoSinDetalle() {
        LibroResponse vacio = new LibroResponse("2026-07", TipoOperacion.COMPRA,
                List.of(), List.of(), new LibroTotales(0, 0, 0, 0, 0, 0, 0, 0), true, null);

        String xml = generator.generar(vacio, emisor(), CARATULA);
        String firmado = new FirmaElectronicaStub().firmarEnveloped(xml, LibroXmlGenerator.ID_ENVIO_LIBRO);

        assertThatCode(() -> validator.validarLibro(firmado)).doesNotThrowAnyException();
        assertThat(xml).contains("<TipoOperacion>COMPRA</TipoOperacion>")
                .doesNotContain("<Detalle>").doesNotContain("<ResumenPeriodo>");
    }

    // ---------- fabricas ----------

    private static LibroResponse libroVentas() {
        return new LibroResponse("2026-07", TipoOperacion.VENTA,
                List.of(new LibroResumenTipo(33, 1, 0, 100000, 0, 19000, 0, 0, 119000,
                        0, 0, 0, List.of())),
                List.of(det(33, 7, 100000, 0, 19000, 0, 119000, 0, 0, null)),
                new LibroTotales(1, 0, 100000, 0, 19000, 0, 0, 119000), false, null);
    }

    private static LibroDetalleDoc det(int tipo, long folio, long neto, long exento, long iva,
                                       long ivaRetenido, long total,
                                       long ivaUsoComun, long ivaNoRec, Integer codIvaNoRec) {
        return new LibroDetalleDoc(tipo, folio, LocalDate.of(2026, 7, 15),
                "77111222-3", "Contraparte de prueba", neto, exento, iva, 0, ivaRetenido, total,
                false, ivaUsoComun, ivaNoRec, codIvaNoRec);
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
