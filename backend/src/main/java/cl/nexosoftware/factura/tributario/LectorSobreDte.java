package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee un sobre {@code EnvioDTE} ajeno (recibido por intercambio) y extrae los
 * datos que necesitan los acuses de respuesta: la caratula, el ID/DigestValue
 * del SetDTE y el resumen de cada DTE. Parseo DOM endurecido (XXE cerrado, sin
 * DOCTYPE), igual que {@link SiiXml} y {@link FirmaElectronicaProd}.
 *
 * Los datos del DTE se leen del {@code Encabezado} (IdDoc/Emisor/Receptor/
 * Totales), NO del TED: el TED usa tags abreviados distintos ({@code RE}, {@code
 * TD}, {@code F}, {@code FE}, {@code RR}, {@code MNT}), asi que buscar por
 * localName dentro del Documento nunca colisiona con ellos.
 */
@Component
public class LectorSobreDte {

    public SobreRecibido leer(String xmlEnvioDte) {
        if (xmlEnvioDte == null || xmlEnvioDte.isBlank()) {
            throw new DteInvalidoException("El sobre EnvioDTE recibido esta vacio");
        }
        Document doc = parsear(xmlEnvioDte);

        Element setDte = primerElemento(doc.getDocumentElement(), "SetDTE");
        if (setDte == null) {
            throw new DteInvalidoException("El XML recibido no es un EnvioDTE: no tiene SetDTE");
        }
        String envioDteId = setDte.getAttribute("ID");

        Element caratula = primerElemento(setDte, "Caratula");
        if (caratula == null) {
            throw new DteInvalidoException("El sobre EnvioDTE no tiene Caratula");
        }
        String rutEmisor = textoDirecto(caratula, "RutEmisor");
        String rutReceptor = textoDirecto(caratula, "RutReceptor");

        String digest = digestDelSet(doc, envioDteId);

        List<SobreRecibido.DteRecibido> documentos = new ArrayList<>();
        NodeList docsDte = setDte.getElementsByTagNameNS("*", "Documento");
        for (int i = 0; i < docsDte.getLength(); i++) {
            documentos.add(leerDocumento((Element) docsDte.item(i)));
        }
        if (documentos.isEmpty()) {
            throw new DteInvalidoException("El sobre EnvioDTE no contiene ningun Documento");
        }
        return new SobreRecibido(rutEmisor, rutReceptor, envioDteId, digest, documentos);
    }

    private SobreRecibido.DteRecibido leerDocumento(Element documento) {
        int tipoDte = Integer.parseInt(requerido(documento, "TipoDTE"));
        long folio = Long.parseLong(requerido(documento, "Folio"));
        LocalDate fchEmis = LocalDate.parse(requerido(documento, "FchEmis"));
        // RUTEmisor/RUTRecep (mayusculas) son los del Encabezado; distintos de
        // RutEmisor/RutReceptor de la caratula y de RE/RR del TED.
        String rutEmisor = requerido(documento, "RUTEmisor");
        String rutRecep = requerido(documento, "RUTRecep");
        long mntTotal = Long.parseLong(requerido(documento, "MntTotal"));
        return new SobreRecibido.DteRecibido(tipoDte, folio, fchEmis, rutEmisor, rutRecep, mntTotal);
    }

    /** DigestValue de la firma cuyo Reference apunta al SetDTE ("#" + id). */
    private String digestDelSet(Document doc, String setId) {
        NodeList refs = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Reference");
        for (int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);
            if (("#" + setId).equals(ref.getAttribute("URI"))) {
                NodeList dv = ref.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "DigestValue");
                if (dv.getLength() > 0) {
                    return dv.item(0).getTextContent().trim();
                }
            }
        }
        return null;
    }

    private String requerido(Element documento, String localName) {
        String v = textoDescendiente(documento, localName);
        if (v == null) {
            throw new DteInvalidoException(
                    "El DTE recibido no tiene el campo obligatorio " + localName);
        }
        return v;
    }

    /** Primer descendiente con ese localName, en cualquier namespace. */
    private String textoDescendiente(Element raiz, String localName) {
        NodeList l = raiz.getElementsByTagNameNS("*", localName);
        return l.getLength() > 0 ? l.item(0).getTextContent().trim() : null;
    }

    /** Texto de un hijo DIRECTO (evita leer campos homonimos anidados). */
    private String textoDirecto(Element padre, String localName) {
        Element e = primerHijo(padre, localName);
        return e != null ? e.getTextContent().trim() : null;
    }

    private Element primerElemento(Element raiz, String localName) {
        NodeList l = raiz.getElementsByTagNameNS("*", localName);
        return l.getLength() > 0 ? (Element) l.item(0) : null;
    }

    private Element primerHijo(Element padre, String localName) {
        NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node n = hijos.item(i);
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private Document parsear(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // StringReader: el string ya esta decodificado; ignora el encoding
            // declarado (ISO-8859-1) y evita re-interpretarlo.
            return dbf.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new DteInvalidoException("El sobre EnvioDTE recibido no es un XML valido: " + e.getMessage());
        }
    }
}
