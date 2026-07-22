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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Construye los libros de compra y venta (IECV) de un periodo tributario.
 *
 * El libro de VENTAS se deriva de los DTE emitidos (via una proyeccion que no
 * carga el XML de cada documento); el de COMPRAS, de los documentos recibidos
 * registrados manualmente. El XML LibroCompraVenta se genera con la estructura
 * del esquema OFICIAL LibroCV_v10; la firma y el envio al SII los orquesta
 * {@link LibroEnvioService}.
 */
@Service
@RequiredArgsConstructor
public class LibroService {

    private final DocumentoRepository documentoRepository;
    private final CompraRepository compraRepository;
    private final EmpresaService empresaService;
    private final LibroXmlGenerator xmlGenerator;

    @Transactional(readOnly = true)
    public LibroResponse libro(Long empresaId, TipoOperacion operacion, YearMonth periodo, Double fctProp) {
        empresaService.buscar(empresaId);
        return construir(empresaId, operacion, periodo, fctProp);
    }

    /** XML LibroCompraVenta con caratula oficial, SIN firmar (para inspeccion). */
    @Transactional(readOnly = true)
    public String libroXml(Long empresaId, TipoOperacion operacion, YearMonth periodo, Double fctProp) {
        Empresa emisor = empresaService.buscar(empresaId);
        return xmlGenerator.generar(
                construir(empresaId, operacion, periodo, fctProp), emisor,
                LibroXmlGenerator.CaratulaLibro.mensual(emisor.getRut()));
    }

    @Transactional(readOnly = true)
    public LibroResponse construir(Long empresaId, TipoOperacion operacion, YearMonth periodo, Double fctProp) {
        return operacion == TipoOperacion.VENTA
                ? construirVentas(documentoRepository.findLibroByEmpresaIdAndFolioNotNullAndFechaEmisionBetween(
                        empresaId, periodo.atDay(1), periodo.atEndOfMonth()), periodo)
                : construirCompras(compraRepository.delPeriodo(empresaId, periodo), periodo, fctProp);
    }

    // ---------- agregacion (pura, testeable como unidad) ----------

    /**
     * Libro de ventas a partir de los documentos foliados del periodo. Excluye
     * RECHAZADOS. Un documento ANULADO por nota de credito va CON montos (la NC
     * materializa la reversa; la marca "A" del IECV es para folios inutilizados,
     * que no producimos). Las boletas van solo resumidas (sin detalle), como en
     * el IECV real. El detalle se ordena por codigo SII y folio (la consulta no
     * puede ordenar por tipo: ordenaria por el nombre del enum).
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
            int tipo = d.getTipoDte().getCodigo();
            Acumulador acc = porTipo.computeIfAbsent(tipo, t -> new Acumulador());
            acc.sumar(d.getNeto(), d.getExento(), d.getIva(),
                    d.getImpuestosAdicionales(), d.getIvaRetenido(), d.getTotal());
            if (!d.getTipoDte().preciosBrutos()) {
                detalle.add(new LibroDetalleDoc(
                        tipo, d.getFolio(), d.getFechaEmision(),
                        d.getReceptorRut(), d.getReceptorRazonSocial(),
                        d.getNeto(), d.getExento(), d.getIva(),
                        d.getImpuestosAdicionales(), d.getIvaRetenido(), d.getTotal(),
                        false, 0, 0, null));
            }
        }
        return armar(TipoOperacion.VENTA, periodo, porTipo, detalle, null);
    }

    /** Libro de compras a partir de los documentos recibidos registrados. */
    static LibroResponse construirCompras(List<DocumentoCompra> compras, YearMonth periodo, Double fctProp) {
        Map<Integer, Acumulador> porTipo = new TreeMap<>();
        List<LibroDetalleDoc> detalle = new ArrayList<>();

        for (DocumentoCompra c : compras) {
            // El IVA del documento va a UN destino: credito normal, uso comun o
            // no recuperable (CompraService valida la exclusion mutua).
            long ivaUsoComun = c.isIvaUsoComun() ? c.getIva() : 0;
            long ivaNoRec = c.getCodIvaNoRec() != null ? c.getIva() : 0;
            long ivaRecuperable = c.getIva() - ivaUsoComun - ivaNoRec;

            Acumulador acc = porTipo.computeIfAbsent(c.getTipoDte(), t -> new Acumulador());
            acc.sumar(c.getNeto(), c.getExento(), ivaRecuperable, 0, c.getIvaRetenido(), c.getTotal());
            acc.sumarUsoComun(ivaUsoComun);
            acc.sumarNoRec(c.getCodIvaNoRec(), ivaNoRec);

            detalle.add(new LibroDetalleDoc(
                    c.getTipoDte(), c.getFolio(), c.getFechaEmision(),
                    c.getRutProveedor(), c.getRazonSocial(),
                    c.getNeto(), c.getExento(), ivaRecuperable, 0, c.getIvaRetenido(), c.getTotal(),
                    false, ivaUsoComun, ivaNoRec, c.getCodIvaNoRec()));
        }
        return armar(TipoOperacion.COMPRA, periodo, porTipo, detalle, fctProp);
    }

    private static LibroResponse armar(TipoOperacion operacion, YearMonth periodo,
                                       Map<Integer, Acumulador> porTipo, List<LibroDetalleDoc> detalle,
                                       Double fctProp) {
        List<LibroResumenTipo> resumen = porTipo.entrySet().stream()
                .map(e -> e.getValue().aResumen(e.getKey(), fctProp))
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
                periodo.toString(), operacion, resumen, detalle, totales, resumen.isEmpty(), fctProp);
    }

    /** Acumulador mutable por tipo de documento. */
    private static final class Acumulador {
        long documentos, anulados, neto, exento, iva, otrosImpuestos, ivaRetenido, total;
        long ivaUsoComun, operacionesIvaUsoComun;
        // LinkedHashMap: el orden de insercion de los codigos es estable.
        final Map<Integer, long[]> ivaNoRecPorCodigo = new LinkedHashMap<>();

        void sumar(long neto, long exento, long iva, long otrosImpuestos, long ivaRetenido, long total) {
            this.documentos++;
            this.neto += neto;
            this.exento += exento;
            this.iva += iva;
            this.otrosImpuestos += otrosImpuestos;
            this.ivaRetenido += ivaRetenido;
            this.total += total;
        }

        void sumarUsoComun(long monto) {
            if (monto > 0) {
                this.ivaUsoComun += monto;
                this.operacionesIvaUsoComun++;
            }
        }

        void sumarNoRec(Integer codigo, long monto) {
            if (codigo != null) {
                long[] acc = ivaNoRecPorCodigo.computeIfAbsent(codigo, c -> new long[2]);
                acc[0]++;
                acc[1] += monto;
            }
        }

        LibroResumenTipo aResumen(int tipo, Double fctProp) {
            long credito = (fctProp != null && ivaUsoComun > 0)
                    ? Math.round(ivaUsoComun * fctProp) : 0;
            List<IvaNoRecResumen> noRec = ivaNoRecPorCodigo.entrySet().stream()
                    .map(e -> new IvaNoRecResumen(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .toList();
            return new LibroResumenTipo(
                    tipo, documentos, anulados, neto, exento, iva, otrosImpuestos, ivaRetenido, total,
                    ivaUsoComun, operacionesIvaUsoComun, credito, noRec);
        }
    }
}
