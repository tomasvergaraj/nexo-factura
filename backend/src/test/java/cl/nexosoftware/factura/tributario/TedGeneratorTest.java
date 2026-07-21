package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.folio.CafData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TED real: aplanado, orden del DD, CAF verbatim, TSTED sin fracciones y —lo
 * central— FRMT verificable con la CLAVE PUBLICA del CAF sobre los bytes
 * ISO-8859-1 del DD, que es exactamente lo que verifica el SII.
 */
class TedGeneratorTest {

    private static final Clock RELOJ_FIJO =
            Clock.fixed(Instant.parse("2026-06-26T18:03:21Z"), ZoneId.of("America/Santiago"));

    private final TedGenerator generator = new TedGenerator(RELOJ_FIJO);
    private final CafData caf = DteFixtures.caf(39);

    @Test
    @DisplayName("el TED es una sola linea con el orden RE,TD,F,FE,RR,RSR,MNT,IT1,CAF,TSTED")
    void estructuraYOrden() {
        DocumentoTributario doc = DteFixtures.boletaAfecta(11900L);
        String ted = generator.generar(doc, DteFixtures.RUT_EMISOR, caf);

        assertThat(ted).startsWith("<TED version=\"1.0\"><DD><RE>76543210-9</RE><TD>39</TD><F>1</F>");
        assertThat(ted).endsWith("</FRMT></TED>");
        // Aplanado: sin saltos de linea FUERA del bloque CAF (que va verbatim).
        String fueraDelCaf = ted.substring(0, ted.indexOf("<CAF"))
                + ted.substring(ted.indexOf("</CAF>") + 6);
        assertThat(fueraDelCaf).doesNotContain("\n").doesNotContain("\t");
        // Orden de los campos del DD.
        int re = ted.indexOf("<RE>"), td = ted.indexOf("<TD>"), f = ted.indexOf("<F>"),
                fe = ted.indexOf("<FE>"), rr = ted.indexOf("<RR>"), rsr = ted.indexOf("<RSR>"),
                mnt = ted.indexOf("<MNT>"), it1 = ted.indexOf("<IT1>"),
                cafIdx = ted.indexOf("<CAF"), tsted = ted.indexOf("<TSTED>");
        assertThat(re).isLessThan(td);
        assertThat(td).isLessThan(f);
        assertThat(f).isLessThan(fe);
        assertThat(fe).isLessThan(rr);
        assertThat(rr).isLessThan(rsr);
        assertThat(rsr).isLessThan(mnt);
        assertThat(mnt).isLessThan(it1);
        assertThat(it1).isLessThan(cafIdx);
        assertThat(cafIdx).isLessThan(tsted);
    }

    @Test
    @DisplayName("un caracter fuera de ISO-8859-1 aborta el timbre (no se degrada a '?' firmado)")
    void caracterFueraDeLatin1Aborta() {
        DocumentoTributario doc = DteFixtures.boletaAfecta(11900L);
        // – = en-dash (tipico texto pegado desde Word); fuente ASCII a proposito.
        doc.setReceptorRazonSocial("PEREZ – SOTO");

        assertThatThrownBy(() -> generator.generar(doc, DteFixtures.RUT_EMISOR, caf))
                .isInstanceOf(DteInvalidoException.class)
                .hasMessageContaining("ISO-8859-1")
                .hasMessageContaining("U+2013");
    }

    @Test
    @DisplayName("el FRMT verifica con la clave publica del CAF sobre el DD en ISO-8859-1")
    void frmtVerificaConClavePublica() throws Exception {
        DocumentoTributario doc = DteFixtures.boletaAfecta(11900L);
        String ted = generator.generar(doc, DteFixtures.RUT_EMISOR, caf);

        String dd = ted.substring(ted.indexOf("<DD>"), ted.indexOf("</DD>") + 5);
        // Ojo: 'algoritmo="SHA1withRSA"' tambien aparece en la FRMA del CAF
        // embebido; el FRMT esta despues del cierre del DD.
        int frmt = ted.indexOf("<FRMT", ted.indexOf("</DD>"));
        String frmtB64 = ted.substring(ted.indexOf('>', frmt) + 1, ted.indexOf("</FRMT>"));

        Signature verificador = Signature.getInstance("SHA1withRSA");
        verificador.initVerify(caf.clavePublica());
        verificador.update(dd.getBytes(StandardCharsets.ISO_8859_1));
        assertThat(verificador.verify(Base64.getDecoder().decode(frmtB64))).isTrue();
    }

    @Test
    @DisplayName("el CAF queda embebido en el DD aplanado: mismos bytes salvo el whitespace entre tags")
    void cafAplanadoDentroDelDd() {
        String ted = generator.generar(DteFixtures.boletaAfecta(11900L), DteFixtures.RUT_EMISOR, caf);
        // La regla de aplanado A.2.4 cubre el DD completo (el SII re-aplana lo
        // recibido antes de verificar el FRMT): el CAF pierde solo el whitespace
        // ENTRE tags; sus valores terminales (FRMA, claves) no se tocan.
        assertThat(ted).contains(caf.cafXmlVerbatim().replaceAll(">\\s+<", "><"));
        assertThat(ted).doesNotContain("\n").doesNotContain("\t");
    }

    @Test
    @DisplayName("TSTED en formato AAAA-MM-DDTHH:MM:SS, hora de Chile, sin fracciones")
    void tstedFormatoExacto() {
        String ted = generator.generar(DteFixtures.boletaAfecta(11900L), DteFixtures.RUT_EMISOR, caf);
        // 2026-06-26T18:03:21Z = 14:03:21 en Chile continental (UTC-4 en invierno).
        assertThat(ted).contains("<TSTED>2026-06-26T14:03:21</TSTED>");
    }

    @Test
    @DisplayName("RSR e IT1 se truncan a 40 y se escapan solo & < > (fidelidad byte con el documento)")
    void truncadoYEscape() {
        DocumentoTributario doc = DteFixtures.boletaAfecta(11900L);
        doc.setReceptorRazonSocial("Sociedad <Ñandú & Cía.> " + "X".repeat(50));
        doc.getLineas().get(0).setNombre("Café \"D'or\" & <croissant>");
        String ted = generator.generar(doc, DteFixtures.RUT_EMISOR, caf);

        String rsr = ted.substring(ted.indexOf("<RSR>") + 5, ted.indexOf("</RSR>"));
        // Truncado ANTES de escapar: el valor logico son 40 chars.
        assertThat(rsr).startsWith("Sociedad &lt;Ñandú &amp; Cía.&gt;");
        // Las comillas NO se escapan (los serializadores DOM tampoco lo hacen;
        // escaparlas rompe la fidelidad byte del DD firmado dentro del documento).
        assertThat(ted).contains("<IT1>Café \"D'or\" &amp; &lt;croissant&gt;</IT1>");
        assertThat(ted).doesNotContain("&quot;").doesNotContain("&apos;");
    }

    @Test
    @DisplayName("un documento sin folio no se puede timbrar")
    void sinFolioLanza() {
        DocumentoTributario doc = DteFixtures.boletaAfecta(11900L);
        doc.setFolio(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> generator.generar(doc, DteFixtures.RUT_EMISOR, caf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("folio");
    }
}
