package cl.nexosoftware.factura.tributario;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Lectura defensiva de las respuestas XML del SII: busca el primer elemento por
 * localName ignorando namespaces (SII:RESPUESTA, envelopes SOAP, etc.).
 */
@Slf4j
final class SiiXml {

    private SiiXml() {}

    /** Primer elemento con ese localName, o null si no existe o el XML no parsea. */
    static String textoElemento(String xml, String localName) {
        if (xml == null) return null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // StringReader: el string ya fue decodificado; asi el parser no
            // re-interpreta el encoding declarado (que a veces miente).
            Document doc = dbf.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            NodeList lista = doc.getElementsByTagNameNS("*", localName);
            return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
        } catch (Exception e) {
            log.warn("XML del SII no parseable buscando {}: {}", localName, e.getMessage());
            return null;
        }
    }
}
