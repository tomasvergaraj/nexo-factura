package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.folio.CafData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La firma XMLDSig del DTE debe verificar TAMBIEN dentro del sobre — es lo que
 * valida el SII (rechazo 505 "Firma DTE Incorrecta" si no). La C14N inclusive
 * hace fragil ese roundtrip: el contexto de namespaces del Documento dentro del
 * sobre debe ser identico al del momento de la firma. Se verifica en los dos
 * modos posibles del verificador del SII: sobre el sobre completo (en contexto)
 * y sobre el DTE extraido como documento standalone.
 */
class FirmaDentroDelSobreTest {

    private static CertificadoFirma certificado;
    private static FirmaElectronicaProd firma;
    private static EnvioBoletaGenerator generadorBoleta;
    private static EnvioDteGenerator generadorDte;

    @BeforeAll
    static void inicializar() {
        AppProperties props = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", "GLOBAL", "sii/cert_prueba.p12", "test123", null, "2026-05-14", 0, "UA"), null);
        certificado = TestCertificados.dummy();
        CertificadoResolver certificadoResolver = TestCertificados.resolver();
        firma = new FirmaElectronicaProd(certificadoResolver);
        DteXmlValidator validator = new DteXmlValidator(true);
        ResolucionResolver resolucion = TestResoluciones.deEntorno(props);
        generadorBoleta = new EnvioBoletaGenerator(firma, validator, certificadoResolver, resolucion, props);
        generadorDte = new EnvioDteGenerator(firma, validator, certificadoResolver, resolucion, props);
    }

    /** Pipeline PROD completo: TED real + firma REAL del DTE (no stub) + sobre. */
    private String dteProdFirmado(DocumentoTributario doc) {
        CafData caf = DteFixtures.caf(doc.getTipoDte().getCodigo() == 39 ? 39 : 33);
        String ted = new TedGenerator().generar(doc, DteFixtures.RUT_EMISOR, caf);
        String xml = new XmlDteGenerator().generar(doc, DteFixtures.emisor(), ted);
        return firma.firmar(xml, 1L);
    }

    @Test
    @DisplayName("boleta: la firma del DTE verifica dentro del sobre y extraida standalone")
    void firmaBoletaVerificaEnElSobre() throws Exception {
        String dte = dteProdFirmado(DteFixtures.boletaAfecta(11900L));
        assertThat(verificarFirmaDelDocumento(dte)).as("firma del DTE standalone").isTrue();

        String sobre = generadorBoleta.generar(new SiiGateway.EnvioSii(1L, dte, 39, 1L, DteFixtures.RUT_EMISOR));
        assertThat(verificarFirmaDelDocumento(sobre)).as("firma del DTE en contexto del sobre").isTrue();
        assertThat(verificarFirmaDelSetDte(sobre))
                .as("firma del SetDTE tras re-declarar namespaces del DTE interno").isTrue();

        String extraido = sobre.substring(sobre.indexOf("<DTE"), sobre.indexOf("</DTE>") + "</DTE>".length());
        assertThat(verificarFirmaDelDocumento(extraido)).as("firma del DTE extraido del sobre").isTrue();
    }

    @Test
    @DisplayName("factura: la firma del DTE verifica dentro del sobre y extraida standalone")
    void firmaFacturaVerificaEnElSobre() throws Exception {
        String dte = dteProdFirmado(DteFixtures.factura(1.0, 10000L, true));
        assertThat(verificarFirmaDelDocumento(dte)).as("firma del DTE standalone").isTrue();

        String sobre = generadorDte.generar(new SiiGateway.EnvioSii(1L, dte, 33, 1L, DteFixtures.RUT_EMISOR));
        assertThat(verificarFirmaDelDocumento(sobre)).as("firma del DTE en contexto del sobre").isTrue();
        assertThat(verificarFirmaDelSetDte(sobre))
                .as("firma del SetDTE tras re-declarar namespaces del DTE interno").isTrue();

        String extraido = sobre.substring(sobre.indexOf("<DTE"), sobre.indexOf("</DTE>") + "</DTE>".length());
        assertThat(verificarFirmaDelDocumento(extraido)).as("firma del DTE extraido del sobre").isTrue();
    }

    /**
     * Valida la firma del sobre (Reference "#SetDoc"): la cirugia de
     * re-declaracion del DTE interno no debe romperla (en C14N inclusive una
     * re-declaracion identica a la heredada no cambia la canonica).
     */
    private boolean verificarFirmaDelSetDte(String sobre) throws Exception {
        Document doc = parsear(sobre);
        NodeList sets = doc.getElementsByTagNameNS("*", "SetDTE");
        assertThat(sets.getLength()).isEqualTo(1);
        ((Element) sets.item(0)).setIdAttribute("ID", true);

        NodeList firmas = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        for (int i = 0; i < firmas.getLength(); i++) {
            Element sig = (Element) firmas.item(i);
            if (!contieneReferencia(sig, "#SetDoc")) {
                continue;
            }
            DOMValidateContext ctx = new DOMValidateContext(certificado.certificado().getPublicKey(), sig);
            ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
            XMLSignature parseada = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
            return parseada.validate(ctx);
        }
        throw new IllegalStateException("No hay firma con Reference #SetDoc en el sobre");
    }

    /**
     * Valida la firma cuya Reference apunta al Documento (URI="#T..F.."),
     * como lo hace el SII. Devuelve false si el digest o la firma no calzan.
     */
    private boolean verificarFirmaDelDocumento(String xml) throws Exception {
        Document doc = parsear(xml);
        NodeList documentos = doc.getElementsByTagNameNS("*", "Documento");
        assertThat(documentos.getLength()).isEqualTo(1);
        Element documento = (Element) documentos.item(0);
        documento.setIdAttribute("ID", true);
        String id = documento.getAttribute("ID");

        NodeList firmas = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        for (int i = 0; i < firmas.getLength(); i++) {
            Element sig = (Element) firmas.item(i);
            if (!contieneReferencia(sig, "#" + id)) {
                continue;
            }
            DOMValidateContext ctx = new DOMValidateContext(certificado.certificado().getPublicKey(), sig);
            ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
            XMLSignature parseada = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
            return parseada.validate(ctx);
        }
        throw new IllegalStateException("No hay firma con Reference al Documento en el XML");
    }

    private boolean contieneReferencia(Element signature, String uri) {
        NodeList refs = signature.getElementsByTagNameNS(XMLSignature.XMLNS, "Reference");
        for (int i = 0; i < refs.getLength(); i++) {
            if (uri.equals(((Element) refs.item(i)).getAttribute("URI"))) {
                return true;
            }
        }
        return false;
    }

    private Document parsear(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)));
    }
}
