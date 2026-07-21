package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.tributario.DteFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser del CAF sobre el fixture SINTETICO (clave RSA-512 propia). Cubre el
 * parseo del DA, el decodificador DER PKCS#1 de la clave privada, la extraccion
 * verbatim del bloque CAF y las validaciones de coherencia.
 */
class CafParserTest {

    private final CafParser parser = new CafParser();

    @Test
    @DisplayName("parsea el DA completo del CAF sintetico")
    void parseaDatosBasicos() {
        CafData data = parser.parsear(DteFixtures.xmlCaf(39));

        assertThat(data.re()).isEqualTo("76543210-9");
        assertThat(data.rs()).isEqualTo("EMPRESA DE PRUEBA SPA");
        assertThat(data.td()).isEqualTo(39);
        assertThat(data.folioDesde()).isEqualTo(1);
        assertThat(data.folioHasta()).isEqualTo(100);
        assertThat(data.fechaAutorizacion()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(data.idk()).isEqualTo(100);
    }

    @Test
    @DisplayName("la clave privada PKCS#1 decodificada firma y la publica del DA verifica")
    void clavesFirmanYVerifican() throws Exception {
        CafData data = parser.parsear(DteFixtures.xmlCaf(39));

        byte[] dd = "<DD><RE>76543210-9</RE></DD>".getBytes(StandardCharsets.ISO_8859_1);
        Signature firmador = Signature.getInstance("SHA1withRSA");
        firmador.initSign(data.clavePrivada());
        firmador.update(dd);
        byte[] firma = firmador.sign();

        Signature verificador = Signature.getInstance("SHA1withRSA");
        verificador.initVerify(data.clavePublica());
        verificador.update(dd);
        assertThat(verificador.verify(firma)).isTrue();
        assertThat(Base64.getEncoder().encodeToString(firma)).isNotBlank();
    }

    @Test
    @DisplayName("el bloque <CAF> se extrae verbatim (byte-identico al archivo)")
    void extraeCafVerbatim() {
        String xml = DteFixtures.xmlCaf(39);
        CafData data = parser.parsear(xml);

        assertThat(data.cafXmlVerbatim()).startsWith("<CAF version=\"1.0\">").endsWith("</CAF>");
        // Substring literal del original, con sus saltos de linea internos.
        assertThat(xml).contains(data.cafXmlVerbatim());
        assertThat(data.cafXmlVerbatim()).contains("<FRMA algoritmo=\"SHA1withRSA\">");
    }

    @Test
    @DisplayName("XML que no es un CAF -> ReglaNegocioException")
    void xmlAjenoLanza() {
        assertThatThrownBy(() -> parser.parsear("<Factura><Total>1</Total></Factura>"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("AUTORIZACION");
    }

    @Test
    @DisplayName("CAF sin RSASK -> ReglaNegocioException")
    void sinClavePrivadaLanza() {
        String sinRsask = DteFixtures.xmlCaf(39)
                .replaceAll("(?s)<RSASK>.*</RSASK>", "");
        assertThatThrownBy(() -> parser.parsear(sinRsask))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("RSASK");
    }

    @Test
    @DisplayName("rango incoherente (H < D) -> ReglaNegocioException")
    void rangoIncoherenteLanza() {
        String malo = DteFixtures.xmlCaf(39)
                .replace("<RNG><D>1</D><H>100</H></RNG>", "<RNG><D>100</D><H>1</H></RNG>");
        assertThatThrownBy(() -> parser.parsear(malo))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("incoherente");
    }

    @Test
    @DisplayName("clave privada que no corresponde a la publica del DA -> ReglaNegocioException")
    void clavesCruzadasLanza() {
        // La RSASK del CAF 33 con el DA (RSAPK) del CAF 39: modulos distintos.
        String caf39 = DteFixtures.xmlCaf(39);
        String caf33 = DteFixtures.xmlCaf(33);
        String rsaskAjena = caf33.substring(caf33.indexOf("<RSASK>"), caf33.indexOf("</RSASK>") + 8);
        String cruzado = caf39.replaceAll("(?s)<RSASK>.*</RSASK>", java.util.regex.Matcher.quoteReplacement(rsaskAjena));

        assertThatThrownBy(() -> parser.parsear(cruzado))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    @DisplayName("elemento obligatorio ausente (FA) -> ReglaNegocioException")
    void faltaElementoLanza() {
        String malo = DteFixtures.xmlCaf(39).replace("<FA>2026-06-01</FA>", "");
        assertThatThrownBy(() -> parser.parsear(malo))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("FA");
    }

    @Test
    @DisplayName("XML vacio o nulo -> ReglaNegocioException")
    void vacioLanza() {
        assertThatThrownBy(() -> parser.parsear("  ")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> parser.parsear(null)).isInstanceOf(ReglaNegocioException.class);
    }
}
