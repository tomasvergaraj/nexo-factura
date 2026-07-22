package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.libro.LibroDtos.IvaNoRecResumen;
import cl.nexosoftware.factura.libro.LibroDtos.LibroDetalleDoc;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResumenTipo;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el XML {@code LibroCompraVenta} (IECV) alineado al esquema OFICIAL
 * {@code LibroCV_v10.xsd}, listo para firmarse (XMLDSig enveloped con Reference
 * al atributo ID del EnvioLibro) y enviarse al SII por el canal clasico.
 *
 * Mapeos tributarios:
 * - ventas: la retencion de cambio de sujeto va como {@code IVARetTotal};
 * - compras: la retencion de la factura de compra (46) va como {@code OtrosImp}
 *   codigo 15 (IVA retenido total), el IVA de uso comun como {@code IVAUsoComun}
 *   con {@code FctProp}/{@code TotCredIVAUsoComun} en el resumen, y el IVA no
 *   recuperable con su codigo (4 = entrega gratuita).
 */
@Component
public class LibroXmlGenerator {

    public static final String ID_ENVIO_LIBRO = "NexoIECV";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int COD_IVA_RETENIDO_TOTAL = 15;

    private final Clock clock;

    public LibroXmlGenerator() {
        this(Clock.system(ZoneId.of("America/Santiago")));
    }

    LibroXmlGenerator(Clock clock) {
        this.clock = clock;
    }

    /** Datos de caratula que no salen del libro agregado. */
    public record CaratulaLibro(String rutEnvia, String fchResol, int nroResol,
                                String tipoLibro, Long folioNotificacion) {

        /** Caratula MENSUAL de inspeccion (sin resolucion real). */
        public static CaratulaLibro mensual(String rutEnvia) {
            return new CaratulaLibro(rutEnvia, "2000-01-01", 0, "MENSUAL", null);
        }
    }

    public String generar(LibroResponse libro, Empresa emisor, CaratulaLibro caratula) {
        ModeloLibro.LibroCompraVenta lcv = new ModeloLibro.LibroCompraVenta();
        ModeloLibro.EnvioLibro envio = new ModeloLibro.EnvioLibro();
        envio.id = ID_ENVIO_LIBRO;
        lcv.envioLibro = envio;

        ModeloLibro.Caratula car = new ModeloLibro.Caratula();
        car.rutEmisorLibro = emisor.getRut();
        car.rutEnvia = caratula.rutEnvia();
        car.periodoTributario = libro.periodo();
        car.fchResol = caratula.fchResol();
        car.nroResol = caratula.nroResol();
        car.tipoOperacion = libro.tipoOperacion().name();
        car.tipoLibro = caratula.tipoLibro();
        car.tipoEnvio = "TOTAL";
        car.folioNotificacion = caratula.folioNotificacion();
        envio.caratula = car;

        boolean compras = libro.tipoOperacion() == TipoOperacion.COMPRA;
        if (!libro.resumen().isEmpty()) {
            ModeloLibro.ResumenPeriodo resumen = new ModeloLibro.ResumenPeriodo();
            resumen.totalesPeriodo = libro.resumen().stream()
                    .map(t -> aTotales(t, libro.fctProp(), compras))
                    .toList();
            envio.resumenPeriodo = resumen;
        }

        List<ModeloLibro.Detalle> detalle = libro.detalle().stream()
                .map(d -> aDetalle(d, compras))
                .toList();
        envio.detalle = detalle.isEmpty() ? null : detalle;

        envio.tmstFirma = LocalDateTime.now(clock).format(TIMESTAMP);

        String xml = JaxbXml.marshalPlano(lcv, "No se pudo generar el XML del libro");
        JaxbXml.exigirLatin1(xml, "el libro IECV");
        return xml;
    }

    private static ModeloLibro.TotalesPeriodo aTotales(LibroResumenTipo t, Double fctProp, boolean compras) {
        ModeloLibro.TotalesPeriodo tp = new ModeloLibro.TotalesPeriodo();
        tp.tpoDoc = t.tipoDocumento();
        tp.totDoc = t.documentos();
        tp.totAnulado = t.anulados() > 0 ? t.anulados() : null;
        tp.totMntExe = t.exento();
        tp.totMntNeto = t.neto();
        tp.totMntIva = t.iva();
        if (!t.ivaNoRec().isEmpty()) {
            List<ModeloLibro.TotIvaNoRec> noRec = new ArrayList<>();
            for (IvaNoRecResumen r : t.ivaNoRec()) {
                ModeloLibro.TotIvaNoRec tnr = new ModeloLibro.TotIvaNoRec();
                tnr.codIvaNoRec = r.codigo();
                tnr.totOpIvaNoRec = r.operaciones();
                tnr.totMntIvaNoRec = r.monto();
                noRec.add(tnr);
            }
            tp.totIvaNoRec = noRec;
        }
        if (t.ivaUsoComun() > 0) {
            tp.totOpIvaUsoComun = t.operacionesIvaUsoComun();
            tp.totIvaUsoComun = t.ivaUsoComun();
            if (fctProp == null) {
                throw new ReglaNegocioException(
                        "El libro tiene IVA de uso comun: informe el factor de proporcionalidad (fctProp)");
            }
            tp.fctProp = fctProp;
            tp.totCredIvaUsoComun = t.creditoIvaUsoComun();
        }
        if (t.otrosImpuestos() > 0) {
            // El agregado de ventas no conserva el desglose por codigo que exige
            // TotOtrosImp: mejor fallar claro que declarar un libro incompleto.
            throw new ReglaNegocioException(
                    "El libro con otros impuestos adicionales aun no esta soportado para el IECV oficial");
        }
        if (t.ivaRetenido() > 0) {
            if (compras) {
                ModeloLibro.TotOtroImp ret = new ModeloLibro.TotOtroImp();
                ret.codImp = COD_IVA_RETENIDO_TOTAL;
                ret.totMntImp = t.ivaRetenido();
                tp.totOtrosImp = List.of(ret);
            } else {
                tp.totOpIvaRetTotal = null; // opcional; el monto basta
                tp.totIvaRetTotal = t.ivaRetenido();
            }
        }
        tp.totMntTotal = t.total();
        return tp;
    }

    private static ModeloLibro.Detalle aDetalle(LibroDetalleDoc d, boolean compras) {
        ModeloLibro.Detalle det = new ModeloLibro.Detalle();
        det.tpoDoc = d.tipoDocumento();
        det.nroDoc = d.folio();
        det.anulado = d.anulado() ? "A" : null;
        det.tpoImp = compras ? 1 : null;
        det.fchDoc = d.fecha().format(FECHA);
        det.rutDoc = d.rutContraparte();
        det.rznSoc = d.razonSocial();
        det.mntExe = d.exento() > 0 ? d.exento() : null;
        det.mntNeto = d.neto();
        det.mntIva = d.iva();
        if (d.ivaNoRec() > 0 && d.codIvaNoRec() != null) {
            ModeloLibro.IvaNoRec nr = new ModeloLibro.IvaNoRec();
            nr.codIvaNoRec = d.codIvaNoRec();
            nr.mntIvaNoRec = d.ivaNoRec();
            det.ivaNoRec = List.of(nr);
        }
        det.ivaUsoComun = d.ivaUsoComun() > 0 ? d.ivaUsoComun() : null;
        if (d.otrosImpuestos() > 0) {
            throw new ReglaNegocioException(
                    "El libro con otros impuestos adicionales aun no esta soportado para el IECV oficial");
        }
        if (d.ivaRetenido() > 0) {
            if (compras) {
                ModeloLibro.OtroImp ret = new ModeloLibro.OtroImp();
                ret.codImp = COD_IVA_RETENIDO_TOTAL;
                ret.tasaImp = 19.0;
                ret.mntImp = d.ivaRetenido();
                det.otrosImp = List.of(ret);
            } else {
                det.ivaRetTotal = d.ivaRetenido();
            }
        }
        det.mntTotal = d.total();
        return det;
    }
}
