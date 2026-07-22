package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.empresa.Empresa;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el XML del DTE a partir del modelo de dominio usando JAXB, en el
 * namespace oficial SiiDte y SIN indentacion (el documento se firma y su timbre
 * debe conservarse byte-identico; ver {@link JaxbXml#marshalPlano}).
 *
 * Boletas (39/41) y facturas/notas (33/34/56/61) usan modelos distintos porque
 * sus esquemas oficiales difieren ({@link ModeloBoleta} vs {@link ModeloDte}).
 * El TED llega ya aplanado y firmado como string ({@link TedGenerator}) y se
 * inserta como subarbol DOM renombrado al namespace SiiDte, de modo que en el
 * documento no aparezca ninguna declaracion xmlns adicional dentro del timbre.
 */
@Component
public class XmlDteGenerator {

    static final String NS_SII_DTE = "http://www.sii.cl/SiiDte";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Clock clock;

    public XmlDteGenerator() {
        this(Clock.system(ZoneId.of("America/Santiago")));
    }

    XmlDteGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generar(DocumentoTributario doc, Empresa emisor, String tedXml) {
        String xml = doc.getTipoDte().preciosBrutos()
                ? generarBoleta(doc, emisor, tedXml)
                : generarFactura(doc, emisor, tedXml);
        // xmlns:xsi declarado en la raiz del DTE ANTES de firmar: el sobre
        // (EnvioBOLETA/EnvioDTE) lo declara en SU raiz, y la C14N INCLUSIVE del
        // SII rinde en el apex (Documento) TODOS los namespaces en scope — si el
        // DTE se firmara sin xsi, dentro del sobre el digest cambiaria y el SII
        // rechazaria con 505 "Firma DTE Incorrecta" (hallado en el E2E de
        // certificacion). Con xsi en ambos contextos, la canonica es identica.
        xml = xml.replace(
                "<DTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\">",
                "<DTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        // El documento se serializa/firma en ISO-8859-1: un caracter no mapeable
        // (em-dash, comillas de Word) se degradaria en silencio a '?'. Corre en
        // TODOS los perfiles para que dev reproduzca lo que prod rechaza.
        JaxbXml.exigirLatin1(xml, "el documento");
        return xml;
    }

    // ---------- factura/notas (33/34/56/61) ----------

    private String generarFactura(DocumentoTributario doc, Empresa emisor, String tedXml) {
        ModeloDte.Dte dte = new ModeloDte.Dte();
        ModeloDte.Documento documento = new ModeloDte.Documento();
        documento.id = idDocumento(doc);

        ModeloDte.Encabezado enc = new ModeloDte.Encabezado();

        ModeloDte.IdDoc idDoc = new ModeloDte.IdDoc();
        idDoc.tipoDte = doc.getTipoDte().getCodigo();
        idDoc.folio = doc.getFolio();
        idDoc.fechaEmision = doc.getFechaEmision().format(FECHA);
        enc.idDoc = idDoc;

        ModeloDte.Emisor em = new ModeloDte.Emisor();
        em.rut = emisor.getRut();
        em.razonSocial = t(emisor.getRazonSocial(), 100);
        em.giro = t(emisor.getGiro(), 80);
        em.acteco = emisor.getActividadEconomica();
        em.direccion = t(emisor.getDireccion(), 70);
        em.comuna = t(emisor.getComuna(), 20);
        enc.emisor = em;

        ModeloDte.Receptor re = new ModeloDte.Receptor();
        re.rut = doc.getReceptorRut();
        re.razonSocial = t(doc.getReceptorRazonSocial(), 100);
        re.giro = t(doc.getReceptorGiro(), 40);
        re.direccion = t(doc.getReceptorDireccion(), 70);
        re.comuna = t(doc.getReceptorComuna(), 20);
        enc.receptor = re;

        ModeloDte.Totales tot = new ModeloDte.Totales();
        // Sin monto afecto (factura exenta 34, nota 100% exenta) se omiten
        // MntNeto/TasaIVA/IVA: el SII rechaza una exenta que declare IVA.
        boolean conAfecto = doc.getNeto() != 0 || doc.getIva() != 0;
        tot.neto = conAfecto ? doc.getNeto() : null;
        tot.exento = doc.getExento();
        tot.tasaIva = conAfecto ? doc.getTasaIva() : null;
        tot.iva = conAfecto ? doc.getIva() : null;
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
            d.indicadorExento = l.isAfecto() ? null : 1;
            d.nombre = t(l.getNombre(), 80);
            d.cantidad = l.getCantidad();
            d.unidad = t(l.getUnidad(), 4);
            d.precioUnitario = l.getPrecioUnitario() > 0 ? l.getPrecioUnitario() : null;
            d.descuentoPct = l.getDescuentoPct();
            d.descuento = l.getDescuentoMonto() > 0 ? l.getDescuentoMonto() : null;
            d.codImpAdic = l.getCodImpAdic();
            d.montoItem = l.getMontoLinea();
            detalles.add(d);
        }
        documento.detalle = detalles;

        // Descuento global sobre afectos (DscRcgGlobal D/%): sin IndExeDR aplica
        // al monto afecto; los totales del encabezado ya vienen rebajados.
        if (doc.getDescuentoGlobalPct() != null) {
            ModeloDte.DscRcgGlobal dr = new ModeloDte.DscRcgGlobal();
            dr.numeroLinea = 1;
            dr.tipoMovimiento = "D";
            dr.tipoValor = "%";
            dr.valor = doc.getDescuentoGlobalPct();
            documento.dscRcgGlobal = List.of(dr);
        }

        // Bloque <Referencia> (obligatorio en notas 56/61): despues del detalle,
        // antes del TED, en el orden de insercion del documento. En certificacion
        // el revisor asocia cada DTE a su caso SOLO por la referencia al set
        // (TpoDocRef=SET, FolioRef=numero de caso, sin CodRef); sin ella el set
        // se rechaza con "El Documento No Esta en el Envio".
        List<ModeloDte.Referencia> referencias = new ArrayList<>();
        int nroRef = 1;
        if (doc.getSetCaso() != null) {
            ModeloDte.Referencia set = new ModeloDte.Referencia();
            set.numeroLinea = nroRef++;
            set.tipoDocumentoRef = "SET";
            set.folioRef = doc.getSetCaso();
            set.fechaRef = doc.getFechaEmision().format(FECHA);
            set.razon = "CASO " + doc.getSetCaso();
            referencias.add(set);
        }
        for (cl.nexosoftware.factura.documento.Referencia r : doc.getReferencias()) {
            ModeloDte.Referencia ref = new ModeloDte.Referencia();
            ref.numeroLinea = nroRef++;
            ref.tipoDocumentoRef = String.valueOf(r.getTipoDocumentoRef());
            ref.folioRef = String.valueOf(r.getFolioRef());
            ref.fechaRef = r.getFechaRef().format(FECHA);
            ref.codigoReferencia = r.getTipoReferencia().getCodigo();
            ref.razon = t(r.getRazon(), 90);
            referencias.add(ref);
        }
        documento.referencias = referencias;

        documento.ted = tedComoDom(tedXml);
        documento.tmstFirma = LocalDateTime.now(clock).format(TIMESTAMP);

        dte.documento = documento;
        return JaxbXml.marshalPlano(dte, "No se pudo generar el XML del DTE");
    }

    // ---------- boletas (39/41) ----------

    private String generarBoleta(DocumentoTributario doc, Empresa emisor, String tedXml) {
        ModeloBoleta.Dte dte = new ModeloBoleta.Dte();
        ModeloBoleta.Documento documento = new ModeloBoleta.Documento();
        documento.id = idDocumento(doc);

        ModeloBoleta.Encabezado enc = new ModeloBoleta.Encabezado();

        ModeloBoleta.IdDoc idDoc = new ModeloBoleta.IdDoc();
        idDoc.tipoDte = doc.getTipoDte().getCodigo();
        idDoc.folio = doc.getFolio();
        idDoc.fechaEmision = doc.getFechaEmision().format(FECHA);
        enc.idDoc = idDoc;

        ModeloBoleta.Emisor em = new ModeloBoleta.Emisor();
        em.rut = emisor.getRut();
        em.razonSocial = t(emisor.getRazonSocial(), 100);
        em.giro = t(emisor.getGiro(), 80);
        em.direccion = t(emisor.getDireccion(), 70);
        em.comuna = t(emisor.getComuna(), 20);
        enc.emisor = em;

        ModeloBoleta.Receptor re = new ModeloBoleta.Receptor();
        re.rut = doc.getReceptorRut();
        re.razonSocial = t(doc.getReceptorRazonSocial(), 100);
        re.direccion = t(doc.getReceptorDireccion(), 70);
        re.comuna = t(doc.getReceptorComuna(), 20);
        enc.receptor = re;

        // Totales de boleta: solo los montos que aplican (el esquema los deja
        // opcionales; el Formato exige MntNeto+IVA en la afecta y MntExe en la
        // exenta). MntTotal siempre.
        ModeloBoleta.Totales tot = new ModeloBoleta.Totales();
        tot.neto = doc.getNeto() > 0 ? doc.getNeto() : null;
        tot.iva = doc.getIva() > 0 ? doc.getIva() : null;
        tot.exento = doc.getExento() > 0 ? doc.getExento() : null;
        tot.total = doc.getTotal();
        enc.totales = tot;

        documento.encabezado = enc;

        List<ModeloBoleta.Detalle> detalles = new ArrayList<>();
        for (LineaDetalle l : doc.getLineas()) {
            ModeloBoleta.Detalle d = new ModeloBoleta.Detalle();
            d.numeroLinea = l.getNumeroLinea();
            d.indicadorExento = l.isAfecto() ? null : 1;
            d.nombre = t(l.getNombre(), 80);
            d.cantidad = l.getCantidad();
            d.unidad = t(l.getUnidad(), 4);
            d.precioUnitario = l.getPrecioUnitario() > 0 ? l.getPrecioUnitario() : null;
            d.descuentoPct = l.getDescuentoPct();
            d.descuento = l.getDescuentoMonto() > 0 ? l.getDescuentoMonto() : null;
            d.montoItem = l.getMontoLinea();
            detalles.add(d);
        }
        documento.detalle = detalles;

        documento.ted = tedComoDom(tedXml);
        documento.tmstFirma = LocalDateTime.now(clock).format(TIMESTAMP);

        dte.documento = documento;
        return JaxbXml.marshalPlano(dte, "No se pudo generar el XML de la boleta");
    }

    // ---------- helpers ----------

    private String idDocumento(DocumentoTributario doc) {
        return "T" + doc.getTipoDte().getCodigo() + "F" + doc.getFolio();
    }

    /**
     * Parsea el TED aplanado y renombra todo su subarbol al namespace SiiDte:
     * asi JAXB lo marshalla dentro del namespace default del documento sin
     * agregar declaraciones xmlns (el string firmado no las lleva, y la regla de
     * aplanado del SII las descarta al verificar el FRMT).
     */
    private Element tedComoDom(String tedXml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // StringReader: el fragmento TED no declara encoding y por bytes el
            // parser asumiria UTF-8 (rompe con acentos/enes en ISO-8859-1).
            Document dom = dbf.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(tedXml)));
            renombrarANamespace(dom, dom.getDocumentElement());
            return dom.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo incorporar el TED al documento", e);
        }
    }

    private void renombrarANamespace(Document dom, Element elemento) {
        NodeList hijos = elemento.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node hijo = hijos.item(i);
            if (hijo instanceof Element e) {
                renombrarANamespace(dom, e);
            }
        }
        dom.renameNode(elemento, NS_SII_DTE, elemento.getTagName());
    }

    /** Trunca a los largos maximos del esquema (un valor largo seria rechazado). */
    private String t(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
