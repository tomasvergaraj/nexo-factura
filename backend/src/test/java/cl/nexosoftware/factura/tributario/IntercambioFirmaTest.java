package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.tributario.EnvioRecibosGenerator.ReciboItem;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.AcuseEnvio;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.Cabecera;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las firmas XMLDSig REALES de los acuses de intercambio verifican como lo hace
 * el SII. Interesa sobre todo el EnvioRecibos, con su DOBLE firma: cada Recibo
 * debe verificar tanto en el contexto del sobre como EXTRAIDO standalone (tras
 * re-declarar su namespace), y la firma del SetRecibos no debe romperse por esa
 * cirugia — el mismo roundtrip fragil que el DTE dentro del sobre EnvioDTE.
 */
class IntercambioFirmaTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-23T14:30:00Z"), ZoneId.of("America/Santiago"));

    private static CertificadoFirma certificado;
    private static RespuestaDteGenerator respuestaGen;
    private static EnvioRecibosGenerator recibosGen;

    @BeforeAll
    static void inicializar() {
        certificado = TestCertificados.dummy();
        FirmaElectronicaProd firma = new FirmaElectronicaProd(TestCertificados.resolver());
        DteXmlValidator validator = new DteXmlValidator(true);
        respuestaGen = new RespuestaDteGenerator(firma, validator, RELOJ);
        recibosGen = new EnvioRecibosGenerator(firma, validator, RELOJ);
    }

    @Test
    @DisplayName("EnvioRecibos: la firma del SetRecibos y la de cada Recibo verifican (en contexto y extraida)")
    void envioRecibosDobleFirmaVerifica() throws Exception {
        ReciboItem recibo = new ReciboItem(33, 52235L, LocalDate.of(2026, 7, 23),
                "88888888-8", "78397017-1", 5390L, "Casa Matriz", certificado.rutFirmante());

        String xml = recibosGen.generar("78397017-1", "88888888-8", Contacto.VACIO, List.of(recibo), 1L);

        assertThat(verificarFirmaConId(xml, "SetRecibos")).as("firma del SetRecibos").isTrue();
        assertThat(verificarFirmaConId(xml, "Recibo52235")).as("firma del Recibo en contexto").isTrue();

        String extraido = xml.substring(xml.indexOf("<Recibo "), xml.indexOf("</Recibo>") + "</Recibo>".length());
        assertThat(verificarFirmaConId(extraido, "Recibo52235")).as("firma del Recibo extraido").isTrue();
    }

    @Test
    @DisplayName("RespuestaDTE (Respuesta de Intercambio): la firma sobre el Resultado verifica")
    void respuestaIntercambioFirmaVerifica() throws Exception {
        SobreRecibido.DteRecibido d = new SobreRecibido.DteRecibido(
                33, 52235L, LocalDate.of(2026, 7, 23), "88888888-8", "78397017-1", 5390L);
        Cabecera cab = new Cabecera("78397017-1", "88888888-8", Contacto.VACIO, 1753278600L);
        AcuseEnvio acuse = new AcuseEnvio("set_intercambio.xml", LocalDateTime.now(RELOJ), 1753278600L,
                "SetDoc", "1WGHYu7oiVjSTV1/Bjcejc02gcA=", "88888888-8", "78397017-1", 0,
                List.of(new DteEvaluado(d, true, 0)));

        String xml = respuestaGen.generarRecepcionEnvio(cab, acuse, 1L);

        assertThat(verificarFirmaConId(xml, "Respuesta")).as("firma del Resultado").isTrue();
    }

    /** Valida la firma cuya Reference apunta al elemento con ese ID (URI="#id"). */
    private boolean verificarFirmaConId(String xml, String id) throws Exception {
        Document doc = parsear(xml);
        registrarIds(doc.getDocumentElement());

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
        throw new IllegalStateException("No hay firma con Reference #" + id + " en el XML");
    }

    /** Registra como xml:id todo atributo ID para que las Reference resuelvan. */
    private void registrarIds(Element el) {
        if (!el.getAttribute("ID").isBlank()) {
            el.setIdAttribute("ID", true);
        }
        NodeList hijos = el.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof Element hijo) {
                registrarIds(hijo);
            }
        }
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
