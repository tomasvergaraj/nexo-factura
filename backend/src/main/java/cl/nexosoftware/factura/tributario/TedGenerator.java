package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.folio.CafData;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Genera el Timbre Electronico (TED) del DTE, firmado con la clave privada del CAF.
 *
 * El TED NO es XMLDSig (Instructivo SII A.2.3-A.2.4): la firma FRMT es
 * {@code base64(SHA1withRSA(bytes ISO-8859-1 del <DD> aplanado))}. Por eso el DD
 * se construye directamente como string en una sola linea, sin espacios entre
 * tags ni referencias a namespaces, con el bloque {@code <CAF>} embebido
 * verbatim tal como lo entrego el SII. El string retornado es la fuente unica
 * del timbre: se embebe tal cual en el XML del DTE y se codifica tal cual en el
 * PDF417 de la representacion impresa.
 *
 * Orden de los hijos del DD (fijado por el esquema oficial):
 * RE, TD, F, FE, RR, RSR, MNT, IT1, CAF, TSTED. RSR e IT1 truncados a 40.
 */
@Component
public class TedGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    // TSTED: AAAA-MM-DDTHH:MM:SS, sin fracciones ni zona (FechaHoraType del SII).
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Clock clock;

    public TedGenerator() {
        this(Clock.system(ZoneId.of("America/Santiago")));
    }

    TedGenerator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Construye el TED aplanado del documento (que ya debe tener folio asignado)
     * y lo firma con la clave privada del CAF del que salio ese folio.
     */
    public String generar(DocumentoTributario doc, String rutEmisor, CafData caf) {
        if (doc.getFolio() == null) {
            throw new IllegalStateException("No se puede timbrar un documento sin folio asignado");
        }
        StringBuilder dd = new StringBuilder(caf.cafXmlVerbatim().length() + 512);
        dd.append("<DD>")
                .append("<RE>").append(escapar(rutEmisor)).append("</RE>")
                .append("<TD>").append(doc.getTipoDte().getCodigo()).append("</TD>")
                .append("<F>").append(doc.getFolio()).append("</F>")
                .append("<FE>").append(doc.getFechaEmision().format(FECHA)).append("</FE>")
                .append("<RR>").append(escapar(doc.getReceptorRut())).append("</RR>")
                .append("<RSR>").append(escapar(abreviar(doc.getReceptorRazonSocial()))).append("</RSR>")
                .append("<MNT>").append(doc.getTotal()).append("</MNT>")
                .append("<IT1>").append(escapar(abreviar(primerItem(doc)))).append("</IT1>")
                .append(aplanar(caf.cafXmlVerbatim()))
                .append("<TSTED>").append(LocalDateTime.now(clock).format(TIMESTAMP)).append("</TSTED>")
                .append("</DD>");

        // Antes de firmar: getBytes(ISO_8859_1) degradaria en silencio a '?' los
        // caracteres no mapeables, y ese '?' quedaria firmado en el FRMT.
        JaxbXml.exigirLatin1(dd.toString(), "el timbre (TED)");

        String frmt = firmar(dd.toString(), caf);
        return "<TED version=\"1.0\">" + dd + "<FRMT algoritmo=\"SHA1withRSA\">" + frmt + "</FRMT></TED>";
    }

    private String firmar(String dd, CafData caf) {
        try {
            Signature firmador = Signature.getInstance("SHA1withRSA");
            firmador.initSign(caf.clavePrivada());
            firmador.update(dd.getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(firmador.sign());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el timbre (FRMT) con la clave del CAF", e);
        }
    }

    private String primerItem(DocumentoTributario doc) {
        return doc.getLineas().isEmpty() ? "" : doc.getLineas().get(0).getNombre();
    }

    /**
     * Aplana el bloque CAF: elimina el whitespace ENTRE tags (la regla A.2.4
     * aplica al DD completo, incluido el CAF embebido — el SII re-aplana lo
     * recibido antes de verificar el FRMT, y un CAF con sus saltos de linea
     * originales produce bytes distintos: reparo 510 "Firma Timbre Electronico
     * Incorrecta", hallado en el E2E de certificacion). Los valores terminales
     * no se tocan: el patron exige un '>' y un '<' a ambos lados del blanco.
     */
    private String aplanar(String cafXml) {
        return cafXml.replaceAll(">\\s+<", "><");
    }

    /** El esquema limita RSR e IT1 a 40 caracteres; el truncado es del emisor. */
    private String abreviar(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40);
    }

    /**
     * Escapa el valor para XML: SOLO {@code & < >}. Las comillas NO se escapan
     * a proposito — el TED viaja por un roundtrip DOM (JAXB al marshallar el
     * documento, Transformer al firmar) y esos serializadores no escapan
     * comillas en texto: si aqui se escribiera &amp;quot;/&amp;apos;, los bytes del DD
     * en el documento final diferirian de los firmados y el SII rechazaria el
     * FRMT. Con este set el escaping es estable a traves de los serializadores.
     */
    private String escapar(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
