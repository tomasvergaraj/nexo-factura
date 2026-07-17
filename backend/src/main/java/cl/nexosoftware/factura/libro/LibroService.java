package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.compra.CompraRepository;
import cl.nexosoftware.factura.compra.DocumentoCompra;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.DocumentoRepository.VentaLibroView;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.libro.LibroDtos.*;
import cl.nexosoftware.factura.tributario.LibroXmlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

/**
 * Construye los libros de compra y venta (IECV) de un periodo tributario.
 *
 * El libro de VENTAS se deriva de los DTE emitidos (via una proyeccion que no
 * carga el XML de cada documento); el de COMPRAS, de los documentos recibidos
 * registrados manualmente. El XML LibroCompraVenta se materializa sin firmar
 * (el envio al SII requiere certificado real), igual que el RCOF y el DTE.
 */
@Service
@RequiredArgsConstructor
public class LibroService {

    private final DocumentoRepository documentoRepository;
    private final CompraRepository compraRepository;
    private final EmpresaService empresaService;
    private final LibroXmlGenerator xmlGenerator;

    @Transactional(readOnly = true)
    public LibroResponse libro(Long empresaId, TipoOperacion operacion, YearMonth periodo) {
        empresaService.buscar(empresaId);
        return construir(empresaId, operacion, periodo);
    }

    @Transactional(readOnly = true)
    public String libroXml(Long empresaId, TipoOperacion operacion, YearMonth periodo) {
        Empresa emisor = empresaService.buscar(empresaId);
        return xmlGenerator.generar(construir(empresaId, operacion, periodo), emisor);
    }

    private LibroResponse construir(Long empresaId, TipoOperacion operacion, YearMonth periodo) {
        return operacion == TipoOperacion.VENTA
                ? construirVentas(documentoRepository.findLibroByEmpresaIdAndFolioNotNullAndFechaEmisionBetween(
                        empresaId, periodo.atDay(1), periodo.atEndOfMonth()), periodo)
                : construirCompras(compraRepository.delPeriodo(empresaId, periodo), periodo);
    }

    // ---------- agregacion (pura, testeable como unidad) ----------

    /**
     * Libro de ventas a partir de los documentos foliados del periodo. Excluye
     * RECHAZADOS; los ANULADOS van marcados con montos en cero; las boletas van
     * solo resumidas (sin detalle), como en el IECV real. El detalle se ordena
     * por codigo SII y folio (la consulta no puede ordenar por tipo: ordenaria
     * por el nombre del enum).
     */
    static LibroResponse construirVentas(List<VentaLibroView> docs, YearMonth periodo) {
        List<VentaLibroView> ordenados = new ArrayList<>(docs);
        ordenados.sort(Comparator
                .comparingInt((VentaLibroView d) -> d.getTipoDte().getCodigo())
                .thenComparingLong(VentaLibroView::getFolio));

        Map<Integer, Acumulador> porTipo = new TreeMap<>();
        List<LibroDetalleDoc> detalle = new ArrayList<>();

        for (VentaLibroView d : ordenados) {
            if (d.getEstado() == EstadoDte.RECHAZADO) {
                continue;
            }
            boolean anulado = d.getEstado() == EstadoDte.ANULADO;
            int tipo = d.getTipoDte().getCodigo();
            Acumulador acc = porTipo.computeIfAbsent(tipo, t -> new Acumulador());
            if (anulado) {
                acc.anulados++;
            } else {
                acc.sumar(d.getNeto(), d.getExento(), d.getIva(),
                        d.getImpuestosAdicionales(), d.getIvaRetenido(), d.getTotal());
            }
            if (!d.getTipoDte().preciosBrutos()) {
                // Un documento anulado se lista con montos en cero (como el IECV).
                LongUnaryOperator monto = v -> anulado ? 0 : v;
                detalle.add(new LibroDetalleDoc(
                        tipo, d.getFolio(), d.getFechaEmision(),
                        d.getReceptorRut(), d.getReceptorRazonSocial(),
                        monto.applyAsLong(d.getNeto()),
                        monto.applyAsLong(d.getExento()),
                        monto.applyAsLong(d.getIva()),
                        monto.applyAsLong(d.getImpuestosAdicionales()),
                        monto.applyAsLong(d.getIvaRetenido()),
                        monto.applyAsLong(d.getTotal()),
                        anulado));
            }
        }
        return armar(TipoOperacion.VENTA, periodo, porTipo, detalle);
    }

    /** Libro de compras a partir de los documentos recibidos registrados. */
    static LibroResponse construirCompras(List<DocumentoCompra> compras, YearMonth periodo) {
        Map<Integer, Acumulador> porTipo = new TreeMap<>();
        List<LibroDetalleDoc> detalle = new ArrayList<>();

        for (DocumentoCompra c : compras) {
            porTipo.computeIfAbsent(c.getTipoDte(), t -> new Acumulador())
                    .sumar(c.getNeto(), c.getExento(), c.getIva(), 0, c.getIvaRetenido(), c.getTotal());
            detalle.add(new LibroDetalleDoc(
                    c.getTipoDte(), c.getFolio(), c.getFechaEmision(),
                    c.getRutProveedor(), c.getRazonSocial(),
                    c.getNeto(), c.getExento(), c.getIva(), 0, c.getIvaRetenido(), c.getTotal(), false));
        }
        return armar(TipoOperacion.COMPRA, periodo, porTipo, detalle);
    }

    private static LibroResponse armar(TipoOperacion operacion, YearMonth periodo,
                                       Map<Integer, Acumulador> porTipo, List<LibroDetalleDoc> detalle) {
        List<LibroResumenTipo> resumen = porTipo.entrySet().stream()
                .map(e -> e.getValue().aResumen(e.getKey()))
                .toList();

        LibroTotales totales = new LibroTotales(
                resumen.stream().mapToLong(LibroResumenTipo::documentos).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::anulados).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::neto).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::exento).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::iva).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::otrosImpuestos).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::ivaRetenido).sum(),
                resumen.stream().mapToLong(LibroResumenTipo::total).sum());

        return new LibroResponse(
                periodo.toString(), operacion, resumen, detalle, totales, resumen.isEmpty());
    }

    /** Acumulador mutable por tipo de documento. */
    private static final class Acumulador {
        long documentos, anulados, neto, exento, iva, otrosImpuestos, ivaRetenido, total;

        void sumar(long neto, long exento, long iva, long otrosImpuestos, long ivaRetenido, long total) {
            this.documentos++;
            this.neto += neto;
            this.exento += exento;
            this.iva += iva;
            this.otrosImpuestos += otrosImpuestos;
            this.ivaRetenido += ivaRetenido;
            this.total += total;
        }

        LibroResumenTipo aResumen(int tipo) {
            return new LibroResumenTipo(
                    tipo, documentos, anulados, neto, exento, iva, otrosImpuestos, ivaRetenido, total);
        }
    }
}
