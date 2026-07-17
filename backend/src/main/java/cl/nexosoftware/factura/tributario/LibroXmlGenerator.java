package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.libro.LibroDtos.LibroDetalleDoc;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResumenTipo;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Construye el XML LibroCompraVenta (IECV) a partir del libro agregado, usando
 * JAXB. NO se firma ni se envia al SII (requiere certificado real); solo
 * materializa la estructura del libro, coherente con el subconjunto de esquema
 * usado en {@link XmlDteGenerator} y {@link RcofXmlGenerator}.
 */
@Component
public class LibroXmlGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    public String generar(LibroResponse libro, Empresa emisor) {
        ModeloLibro.LibroCompraVenta lcv = new ModeloLibro.LibroCompraVenta();
        ModeloLibro.EnvioLibro envio = new ModeloLibro.EnvioLibro();
        lcv.envioLibro = envio;

        ModeloLibro.Caratula car = new ModeloLibro.Caratula();
        car.rutEmisorLibro = emisor.getRut();
        car.periodoTributario = libro.periodo();
        car.tipoOperacion = libro.tipoOperacion().name();
        envio.caratula = car;

        ModeloLibro.ResumenPeriodo resumen = new ModeloLibro.ResumenPeriodo();
        resumen.totalesPeriodo = libro.resumen().stream().map(LibroXmlGenerator::aTotales).toList();
        envio.resumenPeriodo = resumen;

        List<ModeloLibro.Detalle> detalle = libro.detalle().stream()
                .map(LibroXmlGenerator::aDetalle)
                .toList();
        envio.detalle = detalle.isEmpty() ? null : detalle;

        return marshal(lcv);
    }

    private static ModeloLibro.TotalesPeriodo aTotales(LibroResumenTipo t) {
        ModeloLibro.TotalesPeriodo tp = new ModeloLibro.TotalesPeriodo();
        tp.tpoDoc = t.tipoDocumento();
        tp.totDoc = t.documentos();
        tp.totAnulado = t.anulados() > 0 ? t.anulados() : null;
        tp.totMntExe = t.exento();
        tp.totMntNeto = t.neto();
        tp.totMntIva = t.iva();
        tp.totOtrosImp = t.otrosImpuestos() > 0 ? t.otrosImpuestos() : null;
        tp.totIvaRet = t.ivaRetenido() > 0 ? t.ivaRetenido() : null;
        tp.totMntTotal = t.total();
        return tp;
    }

    private static ModeloLibro.Detalle aDetalle(LibroDetalleDoc d) {
        ModeloLibro.Detalle det = new ModeloLibro.Detalle();
        det.tpoDoc = d.tipoDocumento();
        det.nroDoc = d.folio();
        det.anulado = d.anulado() ? "A" : null;
        det.fchDoc = d.fecha().format(FECHA);
        det.rutDoc = d.rutContraparte();
        det.rznSoc = d.razonSocial();
        det.mntExe = d.exento() > 0 ? d.exento() : null;
        det.mntNeto = d.neto();
        det.mntIva = d.iva();
        det.otrosImp = d.otrosImpuestos() > 0 ? d.otrosImpuestos() : null;
        det.ivaRet = d.ivaRetenido() > 0 ? d.ivaRetenido() : null;
        det.mntTotal = d.total();
        return det;
    }

    private String marshal(ModeloLibro.LibroCompraVenta lcv) {
        return JaxbXml.marshal(lcv, "No se pudo generar el XML del libro");
    }
}
