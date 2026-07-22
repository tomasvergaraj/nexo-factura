package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.compra.DocumentoCompra;
import cl.nexosoftware.factura.documento.DocumentoRepository.VentaLibroView;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.libro.LibroDtos.LibroDetalleDoc;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResumenTipo;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro (sin Spring ni BD) de la agregacion de los libros de
 * compra y venta: reglas del IECV sobre anulados, rechazados y boletas.
 */
class LibroServiceTest {

    private static final YearMonth PERIODO = YearMonth.of(2026, 7);

    /** Implementacion simple de la proyeccion del repositorio para los tests. */
    private record Venta(
            TipoDte getTipoDte, Long getFolio, LocalDate getFechaEmision, EstadoDte getEstado,
            String getReceptorRut, String getReceptorRazonSocial,
            long getNeto, long getExento, long getIva,
            long getImpuestosAdicionales, long getIvaRetenido, long getTotal
    ) implements VentaLibroView {}

    @Test
    @DisplayName("el libro de ventas agrega por tipo y detalla los documentos no-boleta")
    void ventasAgregaPorTipo() {
        LibroResponse libro = LibroService.construirVentas(List.of(
                factura(1, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000),
                factura(2, EstadoDte.ENVIADO, 50000, 10000, 9500, 69500),
                notaCredito(1, EstadoDte.ACEPTADO, 20000, 0, 3800, 23800)), PERIODO);

        assertThat(libro.periodo()).isEqualTo("2026-07");
        assertThat(libro.tipoOperacion()).isEqualTo(TipoOperacion.VENTA);
        assertThat(libro.sinMovimiento()).isFalse();
        // Resumen ordenado por codigo de tipo: 33 y luego 61.
        assertThat(libro.resumen()).extracting(LibroResumenTipo::tipoDocumento).containsExactly(33, 61);
        LibroResumenTipo facturas = libro.resumen().get(0);
        assertThat(facturas.documentos()).isEqualTo(2);
        assertThat(facturas.neto()).isEqualTo(150000);
        assertThat(facturas.exento()).isEqualTo(10000);
        assertThat(facturas.iva()).isEqualTo(28500);
        assertThat(facturas.total()).isEqualTo(188500);
        // Detalle: un registro por documento, con la contraparte.
        assertThat(libro.detalle()).hasSize(3);
        assertThat(libro.totales().documentos()).isEqualTo(3);
        assertThat(libro.totales().total()).isEqualTo(188500 + 23800);
    }

    @Test
    @DisplayName("el detalle se ordena por codigo SII y folio (no por nombre del enum)")
    void ventasOrdenaPorCodigoSii() {
        // Alfabeticamente NOTA_CREDITO < NOTA_DEBITO, pero por codigo 56 < 61.
        LibroResponse libro = LibroService.construirVentas(List.of(
                notaCredito(9, EstadoDte.ACEPTADO, 20000, 0, 3800, 23800),
                notaDebito(4, EstadoDte.ACEPTADO, 10000, 0, 1900, 11900),
                factura(7, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000),
                factura(2, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000)), PERIODO);

        assertThat(libro.detalle())
                .extracting(LibroDetalleDoc::tipoDocumento, LibroDetalleDoc::folio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(33, 2L),
                        org.assertj.core.groups.Tuple.tuple(33, 7L),
                        org.assertj.core.groups.Tuple.tuple(56, 4L),
                        org.assertj.core.groups.Tuple.tuple(61, 9L));
    }

    @Test
    @DisplayName("un documento ANULADO por NC va CON montos: la reversa la materializa la propia NC")
    void ventasIncluyeAnuladosConMontos() {
        LibroResponse libro = LibroService.construirVentas(List.of(
                factura(1, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000),
                factura(2, EstadoDte.ANULADO, 50000, 0, 9500, 59500)), PERIODO);

        LibroResumenTipo facturas = libro.resumen().get(0);
        assertThat(facturas.documentos()).isEqualTo(2);
        assertThat(facturas.anulados()).isZero();
        assertThat(facturas.total()).isEqualTo(119000 + 59500);

        // Sin marca "A": la factura anulada va como un documento mas (el SII la
        // tiene ACEPTADA; la NC de anulacion aparece por su propio tipo).
        assertThat(libro.detalle()).noneMatch(LibroDetalleDoc::anulado);
        assertThat(libro.totales().total()).isEqualTo(119000 + 59500);
    }

    @Test
    @DisplayName("un documento RECHAZADO queda fuera del libro de ventas")
    void ventasExcluyeRechazados() {
        LibroResponse libro = LibroService.construirVentas(List.of(
                factura(1, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000),
                factura(2, EstadoDte.RECHAZADO, 100000, 0, 19000, 119000)), PERIODO);

        assertThat(libro.resumen().get(0).documentos()).isEqualTo(1);
        assertThat(libro.detalle()).hasSize(1);
        assertThat(libro.totales().total()).isEqualTo(119000);
    }

    @Test
    @DisplayName("las boletas van resumidas por tipo pero sin detalle por documento")
    void ventasResumeBoletasSinDetalle() {
        LibroResponse libro = LibroService.construirVentas(List.of(
                boleta(10, EstadoDte.ACEPTADO, 8403, 1596, 9999),
                boleta(11, EstadoDte.ACEPTADO, 840, 160, 1000),
                factura(1, EstadoDte.ACEPTADO, 100000, 0, 19000, 119000)), PERIODO);

        assertThat(libro.resumen()).extracting(LibroResumenTipo::tipoDocumento).containsExactly(33, 39);
        LibroResumenTipo boletas = libro.resumen().get(1);
        assertThat(boletas.documentos()).isEqualTo(2);
        assertThat(boletas.total()).isEqualTo(10999);
        // Sin filas de detalle para boletas; la factura si va detallada.
        assertThat(libro.detalle()).hasSize(1);
        assertThat(libro.detalle().get(0).tipoDocumento()).isEqualTo(33);
        // Pero los montos de boletas SI suman a los totales del libro.
        assertThat(libro.totales().total()).isEqualTo(119000 + 10999);
    }

    @Test
    @DisplayName("los otros impuestos y la retencion del DTE viajan al resumen de ventas")
    void ventasIncluyeOtrosImpuestosYRetencion() {
        VentaLibroView conIla = new Venta(TipoDte.FACTURA_AFECTA, 1L, LocalDate.of(2026, 7, 15),
                EstadoDte.ACEPTADO, "77111222-3", "Cliente de prueba",
                100000, 0, 19000, 10000, 0, 129000);
        VentaLibroView conRetencion = new Venta(TipoDte.FACTURA_AFECTA, 2L, LocalDate.of(2026, 7, 15),
                EstadoDte.ACEPTADO, "77111222-3", "Cliente de prueba",
                50000, 0, 9500, 0, 9500, 50000);

        LibroResponse libro = LibroService.construirVentas(List.of(conIla, conRetencion), PERIODO);

        LibroResumenTipo facturas = libro.resumen().get(0);
        assertThat(facturas.otrosImpuestos()).isEqualTo(10000);
        assertThat(facturas.ivaRetenido()).isEqualTo(9500);
        assertThat(facturas.total()).isEqualTo(129000 + 50000);
    }

    @Test
    @DisplayName("un periodo sin documentos produce un libro sin movimiento")
    void ventasSinMovimiento() {
        LibroResponse libro = LibroService.construirVentas(List.of(), PERIODO);

        assertThat(libro.sinMovimiento()).isTrue();
        assertThat(libro.resumen()).isEmpty();
        assertThat(libro.detalle()).isEmpty();
        assertThat(libro.totales().total()).isZero();
    }

    @Test
    @DisplayName("el libro de compras agrega los documentos recibidos por tipo")
    void comprasAgregaPorTipo() {
        LibroResponse libro = LibroService.construirCompras(List.of(
                compra(33, 100, "76543210-9", 100000, 0, 19000, 0, 119000),
                compra(33, 101, "76543210-9", 50000, 0, 9500, 0, 59500),
                compra(34, 7, "60910000-1", 0, 30000, 0, 0, 30000)), PERIODO, null);

        assertThat(libro.tipoOperacion()).isEqualTo(TipoOperacion.COMPRA);
        assertThat(libro.resumen()).extracting(LibroResumenTipo::tipoDocumento).containsExactly(33, 34);
        assertThat(libro.resumen().get(0).documentos()).isEqualTo(2);
        assertThat(libro.resumen().get(0).iva()).isEqualTo(28500);
        assertThat(libro.resumen().get(1).exento()).isEqualTo(30000);
        assertThat(libro.detalle()).hasSize(3);
        assertThat(libro.totales().total()).isEqualTo(119000 + 59500 + 30000);
    }

    @Test
    @DisplayName("la retencion de una factura de compra (46) viaja al detalle y al resumen")
    void comprasIncluyeRetencion() {
        // Cambio de sujeto: el comprador retiene el IVA -> total = neto.
        LibroResponse libro = LibroService.construirCompras(List.of(
                compra(46, 12, "76543210-9", 100000, 0, 19000, 19000, 100000)), PERIODO, null);

        assertThat(libro.resumen().get(0).ivaRetenido()).isEqualTo(19000);
        assertThat(libro.detalle().get(0).ivaRetenido()).isEqualTo(19000);
        assertThat(libro.totales().total()).isEqualTo(100000);
    }

    @Test
    @DisplayName("el IVA de uso comun sale del credito normal y su credito usa el factor")
    void comprasIvaUsoComun() {
        // Caso del set: factura 781, neto 29774, IVA 5657 de uso comun, factor 0,60.
        DocumentoCompra usoComun = compra(30, 781, "76543210-9", 29774, 0, 5657, 0, 35431);
        usoComun.setIvaUsoComun(true);

        LibroResponse libro = LibroService.construirCompras(List.of(usoComun), PERIODO, 0.60);

        LibroResumenTipo r = libro.resumen().get(0);
        assertThat(r.iva()).isZero();                       // no es credito directo
        assertThat(r.ivaUsoComun()).isEqualTo(5657);
        assertThat(r.operacionesIvaUsoComun()).isEqualTo(1);
        assertThat(r.creditoIvaUsoComun()).isEqualTo(3394); // round(5657 * 0,60)
        assertThat(libro.fctProp()).isEqualTo(0.60);
        assertThat(libro.detalle().get(0).ivaUsoComun()).isEqualTo(5657);
        assertThat(libro.detalle().get(0).iva()).isZero();
    }

    @Test
    @DisplayName("la entrega gratuita (cod 4) va como IVA no recuperable, fuera del credito")
    void comprasEntregaGratuita() {
        // Caso del set: FE 67, neto 9962, IVA 1893 sin derecho a credito.
        DocumentoCompra gratuita = compra(33, 67, "76543210-9", 9962, 0, 1893, 0, 11855);
        gratuita.setCodIvaNoRec(4);

        LibroResponse libro = LibroService.construirCompras(List.of(gratuita), PERIODO, null);

        LibroResumenTipo r = libro.resumen().get(0);
        assertThat(r.iva()).isZero();
        assertThat(r.ivaNoRec()).hasSize(1);
        assertThat(r.ivaNoRec().get(0).codigo()).isEqualTo(4);
        assertThat(r.ivaNoRec().get(0).operaciones()).isEqualTo(1);
        assertThat(r.ivaNoRec().get(0).monto()).isEqualTo(1893);
        assertThat(libro.detalle().get(0).ivaNoRec()).isEqualTo(1893);
        assertThat(libro.detalle().get(0).codIvaNoRec()).isEqualTo(4);
    }

    @Test
    @DisplayName("una nota de credito de compra (60) entra al libro como su propio tipo")
    void comprasNotaCredito() {
        LibroResponse libro = LibroService.construirCompras(List.of(
                compra(30, 234, "76543210-9", 20325, 0, 3862, 0, 24187),
                compra(60, 451, "76543210-9", 2712, 0, 515, 0, 3227)), PERIODO, null);

        assertThat(libro.resumen()).extracting(LibroResumenTipo::tipoDocumento).containsExactly(30, 60);
        assertThat(libro.resumen().get(1).iva()).isEqualTo(515);
    }

    // ---------- fabricas ----------

    private static VentaLibroView factura(long folio, EstadoDte estado,
                                          long neto, long exento, long iva, long total) {
        return doc(TipoDte.FACTURA_AFECTA, folio, estado, neto, exento, iva, total);
    }

    private static VentaLibroView notaCredito(long folio, EstadoDte estado,
                                              long neto, long exento, long iva, long total) {
        return doc(TipoDte.NOTA_CREDITO, folio, estado, neto, exento, iva, total);
    }

    private static VentaLibroView notaDebito(long folio, EstadoDte estado,
                                             long neto, long exento, long iva, long total) {
        return doc(TipoDte.NOTA_DEBITO, folio, estado, neto, exento, iva, total);
    }

    private static VentaLibroView boleta(long folio, EstadoDte estado,
                                         long neto, long iva, long total) {
        return doc(TipoDte.BOLETA_AFECTA, folio, estado, neto, 0, iva, total);
    }

    private static VentaLibroView doc(TipoDte tipo, long folio, EstadoDte estado,
                                      long neto, long exento, long iva, long total) {
        return new Venta(tipo, folio, LocalDate.of(2026, 7, 15), estado,
                "77111222-3", "Cliente de prueba", neto, exento, iva, 0, 0, total);
    }

    private static DocumentoCompra compra(int tipo, long folio, String rut,
                                          long neto, long exento, long iva, long ivaRetenido, long total) {
        return DocumentoCompra.builder()
                .empresaId(1L)
                .tipoDte(tipo)
                .folio(folio)
                .rutProveedor(rut)
                .razonSocial("Proveedor de prueba")
                .fechaEmision(LocalDate.of(2026, 7, 10))
                .neto(neto)
                .exento(exento)
                .iva(iva)
                .ivaRetenido(ivaRetenido)
                .total(total)
                .build();
    }
}
