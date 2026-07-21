package cl.nexosoftware.factura.tributario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Firma XMLDSig real con el certificado PKCS#12 del emisor, en el perfil que
 * fija el esquema del SII (xmldsignature_v10.xsd): C14N inclusive
 * (REC-xml-c14n-20010315), rsa-sha1, digest sha1, un unico Transform
 * enveloped-signature, y KeyInfo con KeyValue + X509Data EN ESE ORDEN. SHA-256
 * NO es valido: el XSD restringe los URIs de algoritmo con fixed/enumeracion.
 *
 * El documento se parsea y re-serializa en ISO-8859-1 SIN reformatear (el
 * layout del XML, en particular el TED, no puede cambiar despues de firmar).
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class FirmaElectronicaProd implements FirmaElectronica {

    private final CertificadoDigital certificadoDigital;

    @Override
    public String firmar(String xmlDte) {
        Document doc = parsear(xmlDte);
        Element documento = primerElemento(doc, "Documento");
        if (documento == null) {
            throw new IllegalStateException("El XML del DTE no contiene el elemento Documento");
        }
        String id = documento.getAttribute("ID");
        if (id.isBlank()) {
            throw new IllegalStateException("El elemento Documento no tiene atributo ID para referenciar la firma");
        }
        documento.setIdAttribute("ID", true);
        return firmarDom(doc, "#" + id);
    }

    @Override
    public String firmarEnveloped(String xml, String refId) {
        Document doc = parsear(xml);
        String uri = "";
        if (refId != null) {
            Element referenciado = elementoConId(doc.getDocumentElement(), refId);
            if (referenciado == null) {
                throw new IllegalStateException("No existe un elemento con ID=" + refId + " para referenciar la firma");
            }
            referenciado.setIdAttribute("ID", true);
            uri = "#" + refId;
        }
        return firmarDom(doc, uri);
    }

    private String firmarDom(Document doc, String referenceUri) {
        try {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference(
                    referenceUri,
                    fac.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            // Orden exigido por el XSD del SII: KeyValue y despues X509Data.
            KeyInfo ki = kif.newKeyInfo(List.of(
                    kif.newKeyValue(certificadoDigital.certificado().getPublicKey()),
                    kif.newX509Data(List.of(certificadoDigital.certificado()))));

            DOMSignContext ctx = new DOMSignContext(
                    certificadoDigital.clavePrivada(), doc.getDocumentElement());
            fac.newXMLSignature(si, ki).sign(ctx);
            return serializar(doc);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el XML (XMLDSig)", e);
        }
    }

    // ---------- DOM ----------

    private Document parsear(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // requisito de javax.xml.crypto.dsig
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo parsear el XML a firmar", e);
        }
    }

    private Element primerElemento(Document doc, String localName) {
        NodeList lista = doc.getElementsByTagNameNS("*", localName);
        return lista.getLength() > 0 ? (Element) lista.item(0) : null;
    }

    private Element elementoConId(Element raiz, String id) {
        if (id.equals(raiz.getAttribute("ID"))) {
            return raiz;
        }
        NodeList hijos = raiz.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node hijo = hijos.item(i);
            if (hijo instanceof Element e) {
                Element encontrado = elementoConId(e, id);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    /**
     * Re-serializa en ISO-8859-1, sin indentar (no alterar lo firmado). La
     * declaracion XML se escribe a mano: la del Transformer agrega
     * standalone="no", y el detector de esquemas del SII es literal — con ese
     * atributo responde "SCH-00001: Invalid Schema Name" en ambos canales
     * (hallado en el E2E de certificacion).
     */
    private String serializar(Document doc) {
        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
            tr.setOutputProperty(OutputKeys.INDENT, "no");
            tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            tr.transform(new DOMSource(doc), new StreamResult(sw));
            return JaxbXml.PROLOGO + sw;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el XML firmado", e);
        }
    }
}
