package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.empresa.Empresa;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
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
        try {
            JAXBContext ctx = JAXBContext.newInstance(ModeloDte.Dte.class);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter sw = new StringWriter();
            marshaller.marshal(dte, sw);
            return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + sw;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el XML del DTE", e);
        }
    }
}
