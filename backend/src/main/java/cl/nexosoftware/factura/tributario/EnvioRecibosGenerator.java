package cl.nexosoftware.factura.tributario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera y firma el {@code EnvioRecibos} (esquema {@code EnvioRecibos_v10.xsd} +
 * {@code Recibos_v10.xsd}) — el <b>Recibo de Mercaderias</b> de la Ley 19.983.
 *
 * DOBLE firma, como el sobre EnvioDTE: cada {@code Recibo} se firma standalone
 * sobre su {@code DocumentoRecibo/@ID} y luego se embebe VERBATIM en el {@code
 * SetRecibos}, que a su vez se firma sobre su {@code @ID}. Tras firmar el sobre,
 * se re-declara el namespace de cada {@code Recibo} interno (el serializador de
 * la firma elimina la declaracion redundante, pero el SII verifica cada recibo
 * extrayendolo — mismo mecanismo y arreglo que {@link EnvioGenerator} para el
 * DTE dentro del sobre). Re-declarar un namespace identico al heredado no altera
 * la canonica C14N, asi que la firma del SetRecibos no se rompe.
 */
@Component
public class EnvioRecibosGenerator {

    /** Referencia de la firma del sobre: atributo ID del SetRecibos. */
    static final String ID_SET_RECIBOS = "SetRecibos";
    private static final String NS_SII = "http://www.sii.cl/SiiDte";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final Clock clock;

    // @Autowired explicito: hay un segundo constructor (con Clock, para tests).
    @Autowired
    public EnvioRecibosGenerator(FirmaElectronica firma, DteXmlValidator validator) {
        this(firma, validator, Clock.system(ZoneId.of("America/Santiago")));
    }

    EnvioRecibosGenerator(FirmaElectronica firma, DteXmlValidator validator, Clock clock) {
        this.firma = firma;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * Un recibo de recepcion conforme.
     *
     * @param recinto  lugar donde se materializa la recepcion (obligatorio, <=80)
     * @param rutFirma RUN de quien firma el recibo (el firmante del certificado)
     */
    public record ReciboItem(int tipoDoc, long folio, LocalDate fchEmis, String rutEmisor,
                             String rutRecep, long mntTotal, String recinto, String rutFirma) {}

    public String generar(String rutResponde, String rutRecibe, Contacto contacto,
                          List<ReciboItem> recibos, Long empresaId) {
        if (recibos == null || recibos.isEmpty()) {
            throw new IllegalArgumentException(
                    "EnvioRecibos requiere al menos un Recibo (el XSD exige Recibo 1..N)");
        }
        String ts = LocalDateTime.now(clock).format(TIMESTAMP);

        String caratula = sinDeclaracion(caratulaXml(rutResponde, rutRecibe, contacto, ts));

        StringBuilder recibosXml = new StringBuilder();
        for (ReciboItem item : recibos) {
            recibosXml.append(sinDeclaracion(reciboFirmado(item, ts, empresaId)));
        }

        String sobre = JaxbXml.PROLOGO
                + "<EnvioRecibos xmlns=\"" + NS_SII + "\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"" + NS_SII + " EnvioRecibos_v10.xsd\" version=\"1.0\">"
                + "<SetRecibos ID=\"" + ID_SET_RECIBOS + "\">"
                + caratula
                + recibosXml
                + "</SetRecibos>"
                + "</EnvioRecibos>";

        String firmado = firma.firmarEnveloped(sobre, ID_SET_RECIBOS, empresaId);
        firmado = redeclararNamespaceDelRecibo(firmado);
        JaxbXml.exigirLatin1(firmado, "el EnvioRecibos");
        validator.validarEnvioRecibos(firmado);
        return firmado;
    }

    /** Un {@code <Recibo>} standalone marshallado y firmado sobre DocumentoRecibo/@ID. */
    private String reciboFirmado(ReciboItem item, String ts, Long empresaId) {
        ModeloEnvioRecibos.DocumentoRecibo doc = new ModeloEnvioRecibos.DocumentoRecibo();
        String docId = "Recibo" + item.folio();
        doc.id = docId;
        doc.tipoDoc = item.tipoDoc();
        doc.folio = item.folio();
        doc.fchEmis = item.fchEmis().format(FECHA);
        doc.rutEmisor = item.rutEmisor();
        doc.rutRecep = item.rutRecep();
        doc.mntTotal = item.mntTotal();
        doc.recinto = item.recinto();
        doc.rutFirma = item.rutFirma();
        doc.declaracion = ModeloEnvioRecibos.DECLARACION_LEY_19983;
        doc.tmstFirmaRecibo = ts;

        ModeloEnvioRecibos.Recibo recibo = new ModeloEnvioRecibos.Recibo();
        recibo.documentoRecibo = doc;

        String xml = JaxbXml.marshal(recibo, "No se pudo generar el XML del Recibo");
        // xmlns:xsi en el <Recibo> ANTES de firmar: el sobre lo declara (por el
        // schemaLocation) y la C14N INCLUSIVE lo rinde en el apex del
        // DocumentoRecibo al verificar dentro del sobre. Sin el, el digest del
        // recibo cambiaria en contexto (mismo mecanismo que el DTE dentro del
        // sobre, ver XmlDteGenerator). La declaracion se re-inyecta despues de
        // firmar el sobre en {@link #redeclararNamespaceDelRecibo}.
        xml = xml.replace(
                "<Recibo version=\"1.0\" xmlns=\"" + NS_SII + "\">",
                "<Recibo version=\"1.0\" xmlns=\"" + NS_SII + "\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        return firma.firmarEnveloped(xml, docId, empresaId);
    }

    private String caratulaXml(String rutResponde, String rutRecibe, Contacto contacto, String ts) {
        ModeloEnvioRecibos.Caratula c = new ModeloEnvioRecibos.Caratula();
        c.rutResponde = rutResponde;
        c.rutRecibe = rutRecibe;
        Contacto ct = contacto == null ? Contacto.VACIO : contacto;
        c.nmbContacto = vacioANull(ct.nombre());
        c.fonoContacto = vacioANull(ct.fono());
        c.mailContacto = vacioANull(ct.mail());
        c.tmstFirmaEnv = ts;
        // Quita el xmlns redundante del fragmento (el SetRecibos ya lo declara).
        return JaxbXml.marshal(c, "No se pudo generar la caratula del EnvioRecibos")
                .replace("<Caratula version=\"1.0\" xmlns=\"" + NS_SII + "\">", "<Caratula version=\"1.0\">");
    }

    /**
     * Re-declara {@code xmlns} y {@code xmlns:xsi} en cada {@code <Recibo>}
     * interno tras firmar el sobre. El serializador de la firma elimina ambas
     * declaraciones por redundantes (el EnvioRecibos ya las declara), pero el SII
     * verifica cada recibo EXTRAYENDOLO: sin ellas, el DocumentoRecibo extraido
     * pierde el contexto con que se canonizo (xsi incluido) y su digest no calza.
     * Re-declarar namespaces identicos a los heredados no altera la canonica
     * C14N del SetRecibos, asi que su firma no se rompe. No-op si el serializador
     * los conservo (p.ej. con la firma stub, que no re-serializa).
     */
    private static String redeclararNamespaceDelRecibo(String sobreFirmado) {
        return sobreFirmado.replace(
                "<Recibo version=\"1.0\">",
                "<Recibo version=\"1.0\" xmlns=\"" + NS_SII + "\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
    }

    private static String sinDeclaracion(String xml) {
        int i = xml.indexOf("?>");
        return i >= 0 ? xml.substring(i + 2).stripLeading() : xml;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
