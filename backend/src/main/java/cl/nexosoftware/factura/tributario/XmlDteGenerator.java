package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.empresa.Empresa;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el XML del DTE a partir del modelo de dominio usando JAXB.
 */
@Component
public class XmlDteGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    public String generar(DocumentoTributario doc, Empresa emisor, ModeloDte.Ted ted) {
        ModeloDte.Dte dte = new ModeloDte.Dte();
        ModeloDte.Documento documento = new ModeloDte.Documento();
        documento.id = "T" + doc.getTipoDte().getCodigo() + "F" + doc.getFolio();

        ModeloDte.Encabezado enc = new ModeloDte.Encabezado();

        ModeloDte.IdDoc idDoc = new ModeloDte.IdDoc();
        idDoc.tipoDte = doc.getTipoDte().getCodigo();
        idDoc.folio = doc.getFolio();
        idDoc.fechaEmision = doc.getFechaEmision().format(FECHA);
        enc.idDoc = idDoc;

        ModeloDte.Emisor em = new ModeloDte.Emisor();
        em.rut = emisor.getRut();
        em.razonSocial = emisor.getRazonSocial();
        em.giro = emisor.getGiro();
        em.direccion = emisor.getDireccion();
        em.comuna = emisor.getComuna();
        enc.emisor = em;

        ModeloDte.Receptor re = new ModeloDte.Receptor();
        re.rut = doc.getReceptorRut();
        re.razonSocial = doc.getReceptorRazonSocial();
        re.giro = doc.getReceptorGiro();
        re.direccion = doc.getReceptorDireccion();
        re.comuna = doc.getReceptorComuna();
        enc.receptor = re;

        ModeloDte.Totales tot = new ModeloDte.Totales();
        tot.neto = doc.getNeto();
        tot.exento = doc.getExento();
        tot.tasaIva = doc.getTasaIva();
        tot.iva = doc.getIva();
        // Bloque ImptoReten (otros impuestos): derivado del MISMO desglose que usa la
        // calculadora, para que la suma de los MontoImp reconcilie con MntTotal.
        List<ModeloDte.ImptoReten> impuestos = new ArrayList<>();
        for (var imp : CalculadoraImpuestos.desglosarImpuestos(doc.getLineas())) {
            ModeloDte.ImptoReten ir = new ModeloDte.ImptoReten();
            ir.tipoImp = imp.codigo();
            ir.tasaImp = imp.tasa();
            ir.montoImp = imp.monto();
            impuestos.add(ir);
        }
        tot.imptoReten = impuestos.isEmpty() ? null : impuestos;
        tot.total = doc.getTotal();
        enc.totales = tot;

        documento.encabezado = enc;

        List<ModeloDte.Detalle> detalles = new ArrayList<>();
        for (LineaDetalle l : doc.getLineas()) {
            ModeloDte.Detalle d = new ModeloDte.Detalle();
            d.numeroLinea = l.getNumeroLinea();
            d.nombre = l.getNombre();
            d.cantidad = l.getCantidad();
            d.unidad = l.getUnidad();
            d.precioUnitario = l.getPrecioUnitario();
            d.descuento = l.getDescuentoMonto();
            d.indicadorExento = l.isAfecto() ? null : 1;
            d.codImpAdic = l.getCodImpAdic();
            d.montoItem = l.getMontoLinea();
            detalles.add(d);
        }
        documento.detalle = detalles;

        // Bloque <Referencia> (obligatorio en notas 56/61): se emite despues del
        // detalle y antes del TED, en el orden de insercion del documento.
        List<ModeloDte.Referencia> referencias = new ArrayList<>();
        int nroRef = 1;
        for (cl.nexosoftware.factura.documento.Referencia r : doc.getReferencias()) {
            ModeloDte.Referencia ref = new ModeloDte.Referencia();
            ref.numeroLinea = nroRef++;
            ref.tipoDocumentoRef = r.getTipoDocumentoRef();
            ref.folioRef = r.getFolioRef();
            ref.fechaRef = r.getFechaRef().format(FECHA);
            ref.codigoReferencia = r.getTipoReferencia().getCodigo();
            ref.razon = r.getRazon();
            referencias.add(ref);
        }
        documento.referencias = referencias;

        documento.ted = ted;

        dte.documento = documento;
        return marshal(dte);
    }

    private String marshal(ModeloDte.Dte dte) {
        return JaxbXml.marshal(dte, "No se pudo generar el XML del DTE");
    }
}
