package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Firma XMLDSig real con el certificado DUMMY de prueba (sii/cert_prueba.p12,
 * clave "test123", SERIALNUMBER=11111111-1). Verifica el perfil exacto que fija
 * el XSD del SII y que la firma es criptograficamente valida.
 *
 * Nota JDK: validar firmas SHA-1 propias exige desactivar el secure validation
 * del contexto (org.jcp.xml.dsig.secureValidation=false) — SOLO en tests; el
 * SII valida en su lado y su esquema exige SHA-1.
 */
class FirmaElectronicaProdTest {

    private static FirmaElectronicaProd firma;
    private static CertificadoDigital certificado;

    @BeforeAll
    static void inicializar() throws Exception {
        String path = new ClassPathResource("sii/cert_prueba.p12").getFile().getAbsolutePath();
        AppProperties props = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", path, "test123", null, "2026-05-14", 0, "Mozilla/4.0 (compatible; PROG 1.0)"));
        certificado = new CertificadoDigital(props);
        firma = new FirmaElectronicaProd(certificado);
    }

    @Test
    @DisplayName("extrae el RUT del firmante del SERIALNUMBER del subject")
    void rutFirmanteDelSubject() {
        assertThat(certificado.rutFirmante()).isEqualTo("11111111-1");
    }

    @Test
    @DisplayName("firma el DTE con el perfil del SII: C14N inclusive + rsa-sha1 + sha1 + enveloped")
    void firmaConPerfilSii() throws Exception {
        String xml = generarDteSinFirmar();
        String firmado = firma.firmar(xml);
        Element sig = elementoFirma(firmado);

        assertThat(atributo(sig, "CanonicalizationMethod", "Algorithm"))
                .isEqualTo("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(atributo(sig, "SignatureMethod", "Algorithm"))
                .isEqualTo("http://www.w3.org/2000/09/xmldsig#rsa-sha1");
        assertThat(atributo(sig, "DigestMethod", "Algorithm"))
                .isEqualTo("http://www.w3.org/2000/09/xmldsig#sha1");
        assertThat(atributo(sig, "Reference", "URI")).isEqualTo("#T33F1");
        assertThat(atributo(sig, "Transform", "Algorithm"))
                .isEqualTo("http://www.w3.org/2000/09/xmldsig#enveloped-signature");
    }

    @Test
    @DisplayName("KeyInfo lleva KeyValue y X509Data EN ESE ORDEN (exigencia del XSD del SII)")
    void keyInfoConOrdenDelSii() throws Exception {
        Element sig = elementoFirma(firma.firmar(generarDteSinFirmar()));
        Element keyInfo = (Element) sig.getElementsByTagNameNS(XMLSignature.XMLNS, "KeyInfo").item(0);

        var hijos = keyInfo.getChildNodes();
        var nombres = new java.util.ArrayList<String>();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof Element e) nombres.add(e.getLocalName());
        }
        assertThat(nombres).containsExactly("KeyValue", "X509Data");
        assertThat(keyInfo.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate").getLength())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("la firma valida criptograficamente (JDK, secure validation off)")
    void firmaValida() throws Exception {
        assertThat(validar(firma.firmar(generarDteSinFirmar()))).isTrue();
    }

    @Test
    @DisplayName("el TED se conserva byte-identico despues de firmar y serializar (incluye & < > \" ')")
    void tedIntactoTrasFirmar() {
        var doc = DteFixtures.factura(1.0, 10000L, true);
        // Caracteres conflictivos: el roundtrip JAXB->DOM->Transformer debe
        // re-escaparlos EXACTAMENTE como los firmo el TedGenerator.
        doc.setReceptorRazonSocial("Café \"D'or\" & <Cía>");
        String ted = new TedGenerator().generar(doc, DteFixtures.RUT_EMISOR, DteFixtures.caf(33));
        String xml = new XmlDteGenerator().generar(doc, DteFixtures.emisor(), ted);
        String firmado = firma.firmar(xml);

        assertThat(firmado).contains(ted);
    }

    @Test
    @DisplayName("una mutacion posterior del documento invalida la firma")
    void mutacionInvalidaLaFirma() throws Exception {
        String firmado = firma.firmar(generarDteSinFirmar());
        String adulterado = firmado.replace("<MntTotal>11900</MntTotal>", "<MntTotal>1</MntTotal>");
        assertThat(validar(adulterado)).isFalse();
    }

    @Test
    @DisplayName("firmarEnveloped(null) usa Reference URI=\"\" (perfil del getToken)")
    void envelopedSinReferencia() throws Exception {
        String firmado = firma.firmarEnveloped(
                "<getToken><item><Semilla>012345678901</Semilla></item></getToken>", null);
        Element sig = elementoFirma(firmado);
        assertThat(atributo(sig, "Reference", "URI")).isEmpty();
        assertThat(validar(firmado)).isTrue();
    }

    @Test
    @DisplayName("firmarEnveloped con refId referencia #refId (perfil del SetDTE)")
    void envelopedConReferencia() throws Exception {
        String sobre = "<EnvioBOLETA xmlns=\"http://www.sii.cl/SiiDte\" version=\"1.0\">"
                + "<SetDTE ID=\"SetDoc\"><Caratula version=\"1.0\"><RutEmisor>76543210-9</RutEmisor>"
                + "</Caratula></SetDTE></EnvioBOLETA>";
        String firmado = firma.firmarEnveloped(sobre, "SetDoc");
        Element sig = elementoFirma(firmado);
        assertThat(atributo(sig, "Reference", "URI")).isEqualTo("#SetDoc");
        assertThat(validar(firmado)).isTrue();
    }

    // ---------- helpers ----------

    private String generarDteSinFirmar() {
        var doc = DteFixtures.factura(1.0, 10000L, true);
        String ted = new TedGenerator().generar(doc, DteFixtures.RUT_EMISOR, DteFixtures.caf(33));
        return new XmlDteGenerator().generar(doc, DteFixtures.emisor(), ted);
    }

    private Element elementoFirma(String xml) throws Exception {
        Document dom = parsear(xml);
        NodeList firmas = dom.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertThat(firmas.getLength()).isEqualTo(1);
        Element sig = (Element) firmas.item(0);
        // Ultimo hijo elemento de la raiz (exigencia estructural del SII).
        Element raiz = dom.getDocumentElement();
        Element ultimo = null;
        for (int i = 0; i < raiz.getChildNodes().getLength(); i++) {
            if (raiz.getChildNodes().item(i) instanceof Element e) ultimo = e;
        }
        assertThat(ultimo).isSameAs(sig);
        return sig;
    }

    private boolean validar(String xml) throws Exception {
        Document dom = parsear(xml);
        NodeList firmas = dom.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        Element documento = (Element) dom.getElementsByTagNameNS("*", "Documento").item(0);
        if (documento != null && !documento.getAttribute("ID").isBlank()) {
            documento.setIdAttribute("ID", true);
        }
        Element setDte = (Element) dom.getElementsByTagNameNS("*", "SetDTE").item(0);
        if (setDte != null && !setDte.getAttribute("ID").isBlank()) {
            setDte.setIdAttribute("ID", true);
        }
        DOMValidateContext ctx = new DOMValidateContext(
                certificado.certificado().getPublicKey(), firmas.item(0));
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        try {
            return fac.unmarshalXMLSignature(ctx).validate(ctx);
        } catch (javax.xml.crypto.dsig.XMLSignatureException e) {
            return false;
        }
    }

    private Document parsear(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private String atributo(Element sig, String elemento, String atributo) {
        NodeList lista = sig.getElementsByTagNameNS(XMLSignature.XMLNS, elemento);
        assertThat(lista.getLength()).isGreaterThanOrEqualTo(1);
        return ((Element) lista.item(0)).getAttribute(atributo);
    }
}
