package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.compra.CompraDtos.CompraRequest;
import cl.nexosoftware.factura.compra.CompraService;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integracion (PostgreSQL real via Testcontainers) de los libros de
 * compra y venta: registro de compras (con unicidad), agregacion por periodo y
 * XML LibroCompraVenta.
 */
class LibroCompraVentaIT extends AbstractIntegrationTest {

    private static final YearMonth PERIODO = YearMonth.of(2026, 7);

    @Autowired private LibroService libroService;
    @Autowired private CompraService compraService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private DocumentoRepository documentoRepository;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut(rutUnicoDeTest())
                .razonSocial("Empresa Libros")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();
    }

    @Test
    @DisplayName("el libro de compras refleja las compras registradas del periodo y omite otros periodos")
    void libroComprasDelPeriodo() {
        compraService.crear(empresaId, compra(33, 100, LocalDate.of(2026, 7, 10)));
        compraService.crear(empresaId, compra(33, 101, LocalDate.of(2026, 7, 20)));
        compraService.crear(empresaId, compra(33, 50, LocalDate.of(2026, 6, 30))); // otro periodo

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, null);

        assertThat(libro.detalle()).hasSize(2);
        assertThat(libro.resumen()).hasSize(1);
        assertThat(libro.resumen().get(0).documentos()).isEqualTo(2);
        assertThat(libro.totales().total()).isEqualTo(2 * 119000);
    }

    @Test
    @DisplayName("una compra duplicada (empresa+tipo+folio+proveedor) viola la unicidad")
    void compraDuplicadaVioLaUnicidad() {
        compraService.crear(empresaId, compra(33, 100, LocalDate.of(2026, 7, 10)));

        assertThatThrownBy(() -> compraService.crear(empresaId, compra(33, 100, LocalDate.of(2026, 7, 11))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el libro de ventas incluye los DTE foliados del periodo con sus reglas de estado")
    void libroVentasDelPeriodo() {
        guardarDocumento(TipoDte.FACTURA_AFECTA, 1L, EstadoDte.ACEPTADO);
        guardarDocumento(TipoDte.FACTURA_AFECTA, 2L, EstadoDte.ANULADO);
        guardarDocumento(TipoDte.FACTURA_AFECTA, 3L, EstadoDte.RECHAZADO);
        guardarDocumento(TipoDte.BOLETA_AFECTA, 10L, EstadoDte.ACEPTADO);

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.VENTA, PERIODO, null);

        // Resumen: facturas (la ACEPTADA y la ANULADA suman; el rechazado fuera)
        // y boletas. La anulada va con montos: su reversa la materializa la NC.
        assertThat(libro.resumen()).hasSize(2);
        assertThat(libro.resumen().get(0).documentos()).isEqualTo(2);
        assertThat(libro.resumen().get(0).anulados()).isZero();
        // Detalle: las facturas van detalladas (incluida la anulada); la boleta no.
        assertThat(libro.detalle()).hasSize(2);
        assertThat(libro.detalle()).noneMatch(LibroDtos.LibroDetalleDoc::anulado);
    }

    @Test
    @DisplayName("el XML del libro es bien formado y lleva la caratula del periodo")
    void xmlDelLibro() {
        compraService.crear(empresaId, compra(33, 100, LocalDate.of(2026, 7, 10)));

        String xml = libroService.libroXml(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, null);

        assertThat(xml)
                .startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>")
                .contains("<PeriodoTributario>2026-07</PeriodoTributario>")
                .contains("<TipoOperacion>COMPRA</TipoOperacion>")
                .contains("<NroDoc>100</NroDoc>");
    }

    @Test
    @DisplayName("sin factor configurado ni override, el libro con uso comun queda sin credito proporcional")
    void usoComunSinFactor() {
        compraService.crear(empresaId, compraUsoComun(33, 200, LocalDate.of(2026, 7, 10)));

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, null);

        assertThat(libro.fctProp()).isNull();
        assertThat(libro.tieneIvaUsoComun()).isTrue();
        assertThat(libro.resumen().get(0).ivaUsoComun()).isEqualTo(19000);
        // Sin factor no hay como prorratear: el credito queda en 0 (y el libro no
        // se puede firmar, que es lo que el job reporta con motivo accionable).
        assertThat(libro.resumen().get(0).creditoIvaUsoComun()).isZero();
    }

    @Test
    @DisplayName("el factor configurado en la empresa se aplica sin pasarlo por parametro")
    void usoComunConFactorDeLaEmpresa() {
        empresaRepository.save(conFactor(0.60));
        compraService.crear(empresaId, compraUsoComun(33, 201, LocalDate.of(2026, 7, 10)));

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, null);

        assertThat(libro.fctProp()).isEqualTo(0.60);
        assertThat(libro.resumen().get(0).creditoIvaUsoComun()).isEqualTo(Math.round(19000 * 0.60));
    }

    @Test
    @DisplayName("el factor pasado por parametro gana al configurado en la empresa")
    void elOverrideGanaAlFactorDeLaEmpresa() {
        empresaRepository.save(conFactor(0.60));
        compraService.crear(empresaId, compraUsoComun(33, 202, LocalDate.of(2026, 7, 10)));

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, 0.85);

        assertThat(libro.fctProp()).isEqualTo(0.85);
        assertThat(libro.resumen().get(0).creditoIvaUsoComun()).isEqualTo(Math.round(19000 * 0.85));
    }

    @Test
    @DisplayName("el XML del libro con uso comun lleva FctProp con dos decimales fijos")
    void xmlConFctProp() {
        empresaRepository.save(conFactor(0.60));
        compraService.crear(empresaId, compraUsoComun(33, 203, LocalDate.of(2026, 7, 10)));

        String xml = libroService.libroXml(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO, null);

        // "0.60" y no "0.6": el validador del SII rechaza el segundo.
        assertThat(xml)
                .contains("<FctProp>0.60</FctProp>")
                .contains("<TotCredIVAUsoComun>11400</TotCredIVAUsoComun>");
    }

    @Test
    @DisplayName("el factor sugerido acumula desde enero, excluye rechazados y corta el ano anterior")
    void factorSugeridoAcumuladoDesdeEnero() {
        // Afectas 800.000 + exentas 200.000 = 1.000.000 -> 0.80.
        guardarVenta(1L, EstadoDte.ACEPTADO, LocalDate.of(2026, 3, 4), 800_000, 0);
        guardarVenta(2L, EstadoDte.ACEPTADO, LocalDate.of(2026, 5, 10), 0, 200_000);
        // Ninguno de estos dos debe entrar: un rechazado no es una emision valida,
        // y el acumulado arranca en enero del ano DEL PERIODO.
        guardarVenta(3L, EstadoDte.RECHAZADO, LocalDate.of(2026, 4, 1), 5_000_000, 0);
        guardarVenta(4L, EstadoDte.ACEPTADO, LocalDate.of(2025, 12, 20), 9_000_000, 0);

        var sugerido = libroService.factorSugerido(empresaId, PERIODO);

        assertThat(sugerido.factor()).isEqualTo(0.80);
        assertThat(sugerido.ventasAfectas()).isEqualTo(800_000);
        assertThat(sugerido.ventasExentas()).isEqualTo(200_000);
        assertThat(sugerido.documentos()).isEqualTo(2);
        assertThat(sugerido.desde()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(sugerido.hasta()).isEqualTo(LocalDate.of(2026, 7, 31));
        // El dato que deja ver si el acumulado empieza de verdad en enero: aqui no,
        // arranca en marzo, y por eso el valor se ofrece como pista y no se aplica.
        assertThat(sugerido.primeraEmision()).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    @DisplayName("sin ventas en el rango no hay factor sugerido (null, no NaN ni 0)")
    void factorSugeridoSinVentas() {
        var sugerido = libroService.factorSugerido(empresaId, PERIODO);

        assertThat(sugerido.factor()).isNull();
        assertThat(sugerido.documentos()).isZero();
        assertThat(sugerido.primeraEmision()).isNull();
    }

    private void guardarVenta(Long folio, EstadoDte estado, LocalDate fecha, long neto, long exento) {
        documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folio(folio)
                .estado(estado)
                .fechaEmision(fecha)
                .receptorRut("77111222-3")
                .receptorRazonSocial("Cliente de prueba")
                .neto(neto)
                .exento(exento)
                .tasaIva(19.0)
                .iva(Math.round(neto * 0.19))
                .total(neto + exento + Math.round(neto * 0.19))
                .build());
    }

    /** La empresa sembrada, con el factor de proporcionalidad puesto. */
    private Empresa conFactor(double fctProp) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        empresa.setFctProp(fctProp);
        return empresa;
    }

    private CompraRequest compra(int tipo, long folio, LocalDate fecha) {
        return new CompraRequest(tipo, folio, "76543210-9", "Proveedor SpA",
                fecha, 100000L, 0L, 19000L, null, 119000L, null);
    }

    private CompraRequest compraUsoComun(int tipo, long folio, LocalDate fecha) {
        return new CompraRequest(tipo, folio, "76543210-9", "Proveedor SpA",
                fecha, 100000L, 0L, 19000L, null, 119000L, null, true, null);
    }

    private void guardarDocumento(TipoDte tipo, Long folio, EstadoDte estado) {
        documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(tipo)
                .folio(folio)
                .estado(estado)
                .fechaEmision(LocalDate.of(2026, 7, 15))
                .receptorRut("77111222-3")
                .receptorRazonSocial("Cliente de prueba")
                .neto(100000)
                .exento(0)
                .tasaIva(19.0)
                .iva(19000)
                .total(119000)
                .build());
    }
}
