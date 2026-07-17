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
import java.util.concurrent.ThreadLocalRandom;

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
                .rut("93000000-" + ThreadLocalRandom.current().nextInt(0, 9))
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

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO);

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

        LibroResponse libro = libroService.libro(empresaId, LibroDtos.TipoOperacion.VENTA, PERIODO);

        // Resumen: facturas (1 vigente + 1 anulado; el rechazado fuera) y boletas.
        assertThat(libro.resumen()).hasSize(2);
        assertThat(libro.resumen().get(0).documentos()).isEqualTo(1);
        assertThat(libro.resumen().get(0).anulados()).isEqualTo(1);
        // Detalle: las facturas van detalladas (incluida la anulada); la boleta no.
        assertThat(libro.detalle()).hasSize(2);
        assertThat(libro.detalle()).filteredOn(LibroDtos.LibroDetalleDoc::anulado).hasSize(1);
    }

    @Test
    @DisplayName("el XML del libro es bien formado y lleva la caratula del periodo")
    void xmlDelLibro() {
        compraService.crear(empresaId, compra(33, 100, LocalDate.of(2026, 7, 10)));

        String xml = libroService.libroXml(empresaId, LibroDtos.TipoOperacion.COMPRA, PERIODO);

        assertThat(xml)
                .startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>")
                .contains("<PeriodoTributario>2026-07</PeriodoTributario>")
                .contains("<TipoOperacion>COMPRA</TipoOperacion>")
                .contains("<NroDoc>100</NroDoc>");
    }

    private CompraRequest compra(int tipo, long folio, LocalDate fecha) {
        return new CompraRequest(tipo, folio, "76543210-9", "Proveedor SpA",
                fecha, 100000L, 0L, 19000L, null, 119000L, null);
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
