package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Base comun de los sobres de envio al SII ({@code EnvioBOLETA} y {@code EnvioDTE}):
 * la caratula es identica en ambos esquemas y solo cambian el elemento raiz, el
 * XSD de referencia y el metodo de validacion.
 *
 * El DTE almacenado se embebe VERBATIM (solo se le quita la declaracion XML):
 * su firma interna cubre el Documento y no puede re-serializarse. La caratula
 * usa RutReceptor 60803000-K (SII), NroResol/FchResol resueltos POR EMPRESA
 * (fila propia o fallback de entorno, via {@link ResolucionResolver}) y como
 * RutEnvia el RUN del firmante del certificado DE LA EMPRESA (via
 * {@link CertificadoResolver}). El sobre firmado se valida contra el esquema
 * oficial antes de entregarse: un sobre invalido aborta el envio con el
 * detalle, en vez de quemar un intento contra el SII.
 */
abstract class EnvioGenerator {

    static final String RUT_SII = "60803000-K";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final CertificadoResolver certificadoResolver;
    private final ResolucionResolver resolucionResolver;
    private final Clock clock;

    protected EnvioGenerator(FirmaElectronica firma, DteXmlValidator validator,
                             CertificadoResolver certificadoResolver,
                             ResolucionResolver resolucionResolver,
                             AppProperties props, Clock clock) {
        this.firma = firma;
        this.validator = validator;
        this.certificadoResolver = certificadoResolver;
        this.resolucionResolver = resolucionResolver;
        this.clock = clock;
        // Fail-fast SOLO en modo GLOBAL (el contrato historico del ambiente de
        // certificacion): sin FchResol de entorno toda caratula seria rechazada
        // (RCT). En POR_EMPRESA la resolucion vive en cada empresa y se valida
        // por llamada — en el arranque no se puede saber que empresas la tienen.
        if (FirmaModo.desde(props.sii().firmaModo()) == FirmaModo.GLOBAL) {
            ResolucionResolver.validarFchResol(props.sii().fchResol());
        }
    }

    /** Nombre del elemento raiz del sobre ({@code EnvioBOLETA} o {@code EnvioDTE}). */
    abstract String nombreSobre();

    /** Archivo XSD que declara el schemaLocation del sobre. */
    abstract String esquema();

    /** Valida el sobre ya firmado contra su esquema oficial; lanza si no cumple. */
    abstract void validar(String sobreFirmado);

    /** Devuelve el sobre firmado y validado, listo para entregarse al SII. */
    public String generar(SiiGateway.EnvioSii envio) {
        return generarLote(java.util.List.of(envio));
    }

    /**
     * Sobre con VARIOS DTE firmados (p.ej. un set de pruebas completo): un solo
     * SetDTE, con un SubTotDTE por tipo de documento y todos los DTE embebidos
     * verbatim en el orden dado. Todos deben ser del mismo emisor.
     */
    public String generarLote(java.util.List<SiiGateway.EnvioSii> envios) {
        Long empresaId = envios.get(0).empresaId();
        String rutEmisor = envios.get(0).rutEmisor();
        String rutEnvia = certificadoResolver.paraEmpresa(empresaId).rutFirmante();
        ResolucionResolver.Resolucion resolucion = resolucionResolver.paraCaratula(empresaId);

        // Un SubTotDTE por tipo, en orden de primera aparicion.
        java.util.Map<Integer, Long> porTipo = new java.util.LinkedHashMap<>();
        for (SiiGateway.EnvioSii e : envios) {
            porTipo.merge(e.tipoDte(), 1L, Long::sum);
        }
        StringBuilder subTot = new StringBuilder();
        for (var e : porTipo.entrySet()) {
            subTot.append("<SubTotDTE><TpoDTE>").append(e.getKey())
                    .append("</TpoDTE><NroDTE>").append(e.getValue()).append("</NroDTE></SubTotDTE>");
        }
        StringBuilder dtes = new StringBuilder();
        for (SiiGateway.EnvioSii e : envios) {
            dtes.append(sinDeclaracion(e.xmlFirmado()));
        }
        String caratula = "<Caratula version=\"1.0\">"
                + "<RutEmisor>" + rutEmisor + "</RutEmisor>"
                + "<RutEnvia>" + rutEnvia + "</RutEnvia>"
                + "<RutReceptor>" + RUT_SII + "</RutReceptor>"
                + "<FchResol>" + resolucion.fchResol() + "</FchResol>"
                + "<NroResol>" + resolucion.nroResol() + "</NroResol>"
                + "<TmstFirmaEnv>" + LocalDateTime.now(clock).format(TIMESTAMP) + "</TmstFirmaEnv>"
                + subTot
                + "</Caratula>";

        String sobre = JaxbXml.PROLOGO
                + "<" + nombreSobre() + " xmlns=\"http://www.sii.cl/SiiDte\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"http://www.sii.cl/SiiDte " + esquema() + "\" version=\"1.0\">"
                + "<SetDTE ID=\"SetDoc\">" + caratula + dtes + "</SetDTE>"
                + "</" + nombreSobre() + ">";

        String firmado = firma.firmarEnveloped(sobre, "SetDoc", empresaId);
        firmado = redeclararNamespacesDelDte(firmado);
        validar(firmado);
        return firmado;
    }

    /**
     * Re-inyecta las declaraciones de namespace en el {@code <DTE>} interno del
     * sobre YA firmado. El serializador de la firma elimina las declaraciones
     * redundantes (el sobre ya declara los mismos default y xsi), pero el SII
     * verifica la firma del DTE EXTRAYENDOLO del sobre: sin sus declaraciones,
     * el Documento extraido pierde el contexto con que se canonizo al firmarlo
     * y el digest no calza (rechazo 505 "Firma DTE Incorrecta" — hallado y
     * reproducido en el E2E de certificacion; ver FirmaDentroDelSobreTest).
     * La cirugia es segura para la firma del SetDTE: en C14N inclusive una
     * re-declaracion identica a la heredada no se rinde, asi que la canonica
     * del sobre no cambia. Si el serializador conservo las declaraciones, el
     * patron no matchea y esto es un no-op.
     */
    private static String redeclararNamespacesDelDte(String sobreFirmado) {
        return sobreFirmado.replace(
                "<DTE version=\"1.0\">",
                "<DTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
    }

    final DteXmlValidator validator() {
        return validator;
    }

    private String sinDeclaracion(String xml) {
        int i = xml.indexOf("?>");
        return i >= 0 ? xml.substring(i + 2).stripLeading() : xml;
    }
}
