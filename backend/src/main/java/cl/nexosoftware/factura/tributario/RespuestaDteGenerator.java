package cl.nexosoftware.factura.tributario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera y firma el acuse {@code RespuestaDTE} (esquema {@code
 * RespuestaEnvioDTE_v10.xsd}) en sus dos formas del portal de intercambio:
 * <ul>
 *   <li>{@link #generarRecepcionEnvio} — <b>Respuesta de Intercambio</b>: acuse
 *       de recibo del sobre ({@code Resultado/RecepcionEnvio}) con un {@code
 *       RecepcionDTE} por documento (0 = OK, 3 = RUT receptor no corresponde);</li>
 *   <li>{@link #generarResultadoComercial} — <b>Resultado Aprobacion Comercial</b>
 *       ({@code Resultado/ResultadoDTE}), aceptando los DTE que son para nosotros.</li>
 * </ul>
 * Mecanica igual al libro IECV: modelo JAXB → marshal indentado → inyectar
 * {@code xsi:schemaLocation} → firma XMLDSig enveloped sobre {@code Resultado/@ID}
 * → validar contra el XSD oficial. El documento no lleva TED, por eso puede
 * indentarse (la firma se calcula despues, con C14N).
 */
@Component
public class RespuestaDteGenerator {

    /** Referencia de la firma: atributo ID del Resultado. */
    static final String ID_RESULTADO = "Respuesta";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final Clock clock;

    // @Autowired explicito: hay un segundo constructor (con Clock, para tests).
    @Autowired
    public RespuestaDteGenerator(FirmaElectronica firma, DteXmlValidator validator) {
        this(firma, validator, Clock.system(ZoneId.of("America/Santiago")));
    }

    RespuestaDteGenerator(FirmaElectronica firma, DteXmlValidator validator, Clock clock) {
        this.firma = firma;
        this.validator = validator;
        this.clock = clock;
    }

    /** Cabecera comun de la caratula de ambos acuses. */
    public record Cabecera(String rutResponde, String rutRecibe, Contacto contacto, long idRespuesta) {}

    /** Datos del acuse de recibo del sobre (Respuesta de Intercambio). */
    public record AcuseEnvio(String nmbEnvio, LocalDateTime fchRecep, long codEnvio,
                             String envioDteId, String digest, String rutEmisorSobre,
                             String rutReceptorSobre, int estadoRecepEnv, List<DteEvaluado> dtes) {}

    // ---------- Respuesta de Intercambio (RecepcionEnvio) ----------

    public String generarRecepcionEnvio(Cabecera cab, AcuseEnvio acuse) {
        ModeloRespuestaDte.RecepcionEnvio rec = new ModeloRespuestaDte.RecepcionEnvio();
        rec.nmbEnvio = acuse.nmbEnvio();
        rec.fchRecep = acuse.fchRecep().format(TIMESTAMP);
        rec.codEnvio = acuse.codEnvio();
        rec.envioDteId = acuse.envioDteId();
        rec.digest = acuse.digest();
        rec.rutEmisor = acuse.rutEmisorSobre();
        rec.rutReceptor = acuse.rutReceptorSobre();
        rec.estadoRecepEnv = acuse.estadoRecepEnv();
        rec.recepEnvGlosa = glosaRecepEnv(acuse.estadoRecepEnv());
        rec.nroDte = acuse.dtes().size();
        rec.recepcionDte = new ArrayList<>();
        for (DteEvaluado ev : acuse.dtes()) {
            SobreRecibido.DteRecibido d = ev.documento();
            ModeloRespuestaDte.RecepcionDte rd = new ModeloRespuestaDte.RecepcionDte();
            rd.tipoDte = d.tipoDte();
            rd.folio = d.folio();
            rd.fchEmis = d.fchEmis().format(FECHA);
            rd.rutEmisor = d.rutEmisor();
            rd.rutRecep = d.rutReceptor();
            rd.mntTotal = d.mntTotal();
            rd.estadoRecepDte = ev.estadoRecepDte();
            rd.recepDteGlosa = glosaRecepDte(ev.estadoRecepDte());
            rec.recepcionDte.add(rd);
        }

        ModeloRespuestaDte.Resultado resultado = new ModeloRespuestaDte.Resultado();
        resultado.id = ID_RESULTADO;
        resultado.caratula = caratula(cab, 1); // NroDetalles = numero de RecepcionEnvio
        resultado.recepcionEnvio = List.of(rec);

        return armar(resultado);
    }

    // ---------- Resultado Aprobacion Comercial (ResultadoDTE) ----------

    public String generarResultadoComercial(Cabecera cab, long codEnvio, List<DteEvaluado> aceptados) {
        List<ModeloRespuestaDte.ResultadoDte> resultados = new ArrayList<>();
        for (DteEvaluado ev : aceptados) {
            SobreRecibido.DteRecibido d = ev.documento();
            ModeloRespuestaDte.ResultadoDte rd = new ModeloRespuestaDte.ResultadoDte();
            rd.tipoDte = d.tipoDte();
            rd.folio = d.folio();
            rd.fchEmis = d.fchEmis().format(FECHA);
            rd.rutEmisor = d.rutEmisor();
            rd.rutRecep = d.rutReceptor();
            rd.mntTotal = d.mntTotal();
            rd.codEnvio = codEnvio;
            rd.estadoDte = 0; // DTE Aceptado OK
            rd.estadoDteGlosa = "DTE Aceptado OK";
            rd.codRchDsc = null; // solo en rechazo/discrepancia
            resultados.add(rd);
        }

        ModeloRespuestaDte.Resultado resultado = new ModeloRespuestaDte.Resultado();
        resultado.id = ID_RESULTADO;
        resultado.caratula = caratula(cab, resultados.size()); // NroDetalles = numero de DTE
        resultado.resultadoDte = resultados;

        return armar(resultado);
    }

    // ---------- comun ----------

    private ModeloRespuestaDte.Caratula caratula(Cabecera cab, int nroDetalles) {
        ModeloRespuestaDte.Caratula c = new ModeloRespuestaDte.Caratula();
        c.rutResponde = cab.rutResponde();
        c.rutRecibe = cab.rutRecibe();
        c.idRespuesta = cab.idRespuesta();
        c.nroDetalles = nroDetalles;
        Contacto ct = cab.contacto() == null ? Contacto.VACIO : cab.contacto();
        c.nmbContacto = vacioANull(ct.nombre());
        c.fonoContacto = vacioANull(ct.fono());
        c.mailContacto = vacioANull(ct.mail());
        c.tmstFirmaResp = LocalDateTime.now(clock).format(TIMESTAMP);
        return c;
    }

    private String armar(ModeloRespuestaDte.Resultado resultado) {
        ModeloRespuestaDte.RespuestaDte raiz = new ModeloRespuestaDte.RespuestaDte();
        raiz.resultado = resultado;

        String xml = JaxbXml.marshal(raiz, "No se pudo generar el XML de la RespuestaDTE");
        // schemaLocation como en el sobre EnvioDTE del propio SII y el libro IECV.
        xml = xml.replace(
                "<RespuestaDTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\">",
                "<RespuestaDTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"http://www.sii.cl/SiiDte RespuestaEnvioDTE_v10.xsd\">");
        JaxbXml.exigirLatin1(xml, "la RespuestaDTE");

        String firmado = firma.firmarEnveloped(xml, ID_RESULTADO);
        validator.validarRespuestaDte(firmado);
        return firmado;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String glosaRecepEnv(int estado) {
        return switch (estado) {
            case 0 -> "Envio Recibido Conforme";
            case 1 -> "Envio Rechazado - Error de Schema";
            case 2 -> "Envio Rechazado - Error de Firma";
            case 3 -> "Envio Rechazado - RUT Receptor No Corresponde";
            case 90 -> "Envio Rechazado - Archivo Repetido";
            case 91 -> "Envio Rechazado - Archivo Ilegible";
            default -> "Envio Rechazado - Otros";
        };
    }

    private static String glosaRecepDte(int estado) {
        return switch (estado) {
            case 0 -> "DTE Recibido OK";
            case 1 -> "DTE No Recibido - Error de Firma";
            case 2 -> "DTE No Recibido - Error en RUT Emisor";
            case 3 -> "DTE No Recibido - Error en RUT Receptor";
            case 4 -> "DTE No Recibido - DTE Repetido";
            default -> "DTE No Recibido - Otros";
        };
    }
}
