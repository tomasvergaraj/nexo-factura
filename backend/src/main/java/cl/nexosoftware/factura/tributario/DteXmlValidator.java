package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ApiError;
import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import cl.nexosoftware.factura.documento.TipoDte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida los XML tributarios contra los esquemas OFICIALES del SII
 * (resources/sii/oficial, namespace SiiDte), DESPUES de firmar: los XSD exigen
 * el nodo Signature, asi que el orden en la emision es generar → firmar →
 * validar (todo en la misma transaccion; un XML invalido revierte el folio).
 *
 * Dos esquemas compilados e independientes — ambos definen tipos del mismo
 * namespace y NO pueden cargarse juntos:
 * <ul>
 *   <li>facturas/notas: {@code DTE_v10.xsd} (elemento global DTE);</li>
 *   <li>boletas: {@code BoletaDte-local.xsd} (wrapper local que expone el DTE
 *       de boleta como raiz) e incluye {@code EnvioBOLETA_v11.xsd}, por lo que
 *       tambien valida el sobre EnvioBOLETA completo antes de enviarlo.</li>
 * </ul>
 *
 * Thread-safety: los {@link Schema} son inmutables; el {@link Validator} NO lo
 * es y se crea por llamada. Seguridad (XXE): SchemaFactory y Validator
 * endurecidos con acceso externo CERRADO ("" — ni file ni http); los
 * include/import entre los XSD vendoreados se resuelven con un
 * LSResourceResolver de classpath, que ademas es lo unico que funciona dentro
 * del fat jar de Spring Boot (protocolo {@code nested:}, que la allowlist de
 * JAXP no reconoce).
 */
@Component
public class DteXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(DteXmlValidator.class);
    // EnvioDTE_v10 incluye DTE_v10: un solo Schema valida tanto el DTE suelto
    // de factura/notas como el sobre EnvioDTE completo.
    private static final String XSD_FACTURA = "sii/oficial/EnvioDTE_v10.xsd";
    private static final String XSD_BOLETA = "sii/oficial/BoletaDte-local.xsd";
    // Esquema del IECV: define sus propios tipos del namespace SiiDte, por eso
    // se compila como un tercer Schema independiente.
    private static final String XSD_LIBRO = "sii/oficial/LibroCV_v10.xsd";
    // Acuses de intercambio (Ley 19.983 / etapa de intercambio): tambien definen
    // tipos del namespace SiiDte, cada uno como un Schema aparte.
    private static final String XSD_RESPUESTA = "sii/oficial/RespuestaEnvioDTE_v10.xsd";
    private static final String XSD_RECIBOS = "sii/oficial/EnvioRecibos_v10.xsd";

    private final Schema schemaFactura;
    private final Schema schemaBoleta;
    private final Schema schemaLibro;
    private final Schema schemaRespuesta;
    private final Schema schemaRecibos;
    private final boolean habilitado;

    public DteXmlValidator(@Value("${app.dte.validar-xsd:true}") boolean habilitado) {
        this.habilitado = habilitado;
        this.schemaFactura = compilarSchema(XSD_FACTURA);
        this.schemaBoleta = compilarSchema(XSD_BOLETA);
        this.schemaLibro = compilarSchema(XSD_LIBRO);
        this.schemaRespuesta = compilarSchema(XSD_RESPUESTA);
        this.schemaRecibos = compilarSchema(XSD_RECIBOS);
        if (!habilitado) {
            log.warn("Validacion XSD del DTE DESHABILITADA (app.dte.validar-xsd=false). "
                    + "Solo para diagnostico, nunca en produccion.");
        }
    }

    private Schema compilarSchema(String classpath) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // Acceso externo CERRADO: los include/import entre los XSD oficiales
            // (SiiTypes, xmldsignature, DTE_v10) se sirven desde el classpath con
            // el resolver de abajo. Una allowlist de protocolos no sirve aca: en
            // el fat jar de Boot los recursos viven bajo el protocolo "nested:",
            // que JAXP no reconoce (fallaba el arranque en prod).
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo endurecer la SchemaFactory XSD", e);
        }
        factory.setResourceResolver(DteXmlValidator::resolverXsdDelClasspath);

        ClassPathResource recurso = new ClassPathResource(classpath);
        if (!recurso.exists()) {
            throw new IllegalStateException(
                    "No se encontro el esquema XSD en el classpath: " + classpath);
        }
        try (InputStream in = recurso.getInputStream()) {
            // El systemId conserva el nombre para los mensajes de error.
            return factory.newSchema(new StreamSource(in, classpath));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo compilar el esquema XSD (" + classpath + ")", e);
        }
    }

    /**
     * Resuelve un schemaLocation relativo de los XSD vendoreados a su recurso en
     * {@code sii/oficial/}. Devuelve null si no lo conoce (JAXP intentara el
     * acceso externo, que esta cerrado, y fallara con claridad).
     */
    private static LSInput resolverXsdDelClasspath(String type, String namespaceURI, String publicId,
                                                   String systemId, String baseURI) {
        if (systemId == null) {
            return null;
        }
        String nombre = systemId.substring(systemId.lastIndexOf('/') + 1);
        ClassPathResource recurso = new ClassPathResource("sii/oficial/" + nombre);
        if (!recurso.exists()) {
            return null;
        }
        try {
            return new XsdClasspathInput(recurso.getInputStream(), systemId, publicId, baseURI);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el XSD del classpath: " + nombre, e);
        }
    }

    /**
     * Valida el XML FIRMADO del DTE contra el esquema oficial del tipo,
     * acumulando TODOS los errores.
     *
     * @throws DteInvalidoException si el XML no cumple el esquema (422).
     */
    public void validar(String xml, TipoDte tipo) {
        validarContra(tipo.preciosBrutos() ? schemaBoleta : schemaFactura, xml,
                "El XML del DTE generado no cumple el esquema oficial del SII");
    }

    /** Valida el sobre EnvioBOLETA firmado, antes de enviarlo al SII. */
    public void validarEnvioBoleta(String xmlEnvio) {
        validarContra(schemaBoleta, xmlEnvio,
                "El sobre EnvioBOLETA no cumple el esquema oficial del SII");
    }

    /** Valida el sobre EnvioDTE firmado (facturas/notas), antes de enviarlo al SII. */
    public void validarEnvioDte(String xmlEnvio) {
        validarContra(schemaFactura, xmlEnvio,
                "El sobre EnvioDTE no cumple el esquema oficial del SII");
    }

    /** Valida el LibroCompraVenta (IECV) firmado contra LibroCV_v10, antes de enviarlo. */
    public void validarLibro(String xmlLibro) {
        validarContra(schemaLibro, xmlLibro,
                "El libro IECV no cumple el esquema oficial del SII (LibroCV_v10)");
    }

    /** Valida la RespuestaDTE firmada contra RespuestaEnvioDTE_v10 (acuses de intercambio). */
    public void validarRespuestaDte(String xmlRespuesta) {
        validarContra(schemaRespuesta, xmlRespuesta,
                "La RespuestaDTE no cumple el esquema oficial del SII (RespuestaEnvioDTE_v10)");
    }

    /** Valida el EnvioRecibos firmado contra EnvioRecibos_v10 (recibo de mercaderias). */
    public void validarEnvioRecibos(String xmlRecibos) {
        validarContra(schemaRecibos, xmlRecibos,
                "El EnvioRecibos no cumple el esquema oficial del SII (EnvioRecibos_v10)");
    }

    private void validarContra(Schema schema, String xml, String mensaje) {
        if (!habilitado) {
            return;
        }
        Validator validator = schema.newValidator(); // por-llamada: Validator NO es thread-safe
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo endurecer el Validator XSD", e);
        }

        ColectorErrores colector = new ColectorErrores();
        validator.setErrorHandler(colector);

        try {
            // StringReader: el String vive en memoria (UTF-16); ignora la
            // declaracion ISO-8859-1 y evita MalformedByteSequenceException.
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            // ErrorHandler no relanza => un SAXException aqui es fatal/inesperado.
            colector.errores.add(new ApiError.CampoInvalido(
                    "xml", "Error fatal al validar el XML: " + e.getMessage()));
        } catch (IOException e) {
            throw new IllegalStateException("Error de E/S validando el XML", e);
        }

        if (!colector.errores.isEmpty()) {
            throw new DteInvalidoException(
                    mensaje + " (" + colector.errores.size() + " error(es))",
                    List.copyOf(colector.errores));
        }
    }

    /** LSInput minimo: entrega el XSD como stream del classpath; setters no-op. */
    private static final class XsdClasspathInput implements LSInput {
        private final InputStream byteStream;
        private final String systemId;
        private final String publicId;
        private final String baseURI;

        private XsdClasspathInput(InputStream byteStream, String systemId, String publicId, String baseURI) {
            this.byteStream = byteStream;
            this.systemId = systemId;
            this.publicId = publicId;
            this.baseURI = baseURI;
        }

        @Override public InputStream getByteStream() { return byteStream; }
        @Override public String getSystemId() { return systemId; }
        @Override public String getPublicId() { return publicId; }
        @Override public String getBaseURI() { return baseURI; }
        @Override public java.io.Reader getCharacterStream() { return null; }
        @Override public String getStringData() { return null; }
        @Override public String getEncoding() { return null; }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCharacterStream(java.io.Reader r) { }
        @Override public void setByteStream(InputStream b) { }
        @Override public void setStringData(String s) { }
        @Override public void setSystemId(String s) { }
        @Override public void setPublicId(String p) { }
        @Override public void setBaseURI(String b) { }
        @Override public void setEncoding(String e) { }
        @Override public void setCertifiedText(boolean c) { }
    }

    /** Acumula todos los errores de esquema en lugar de fallar en el primero. */
    private static final class ColectorErrores implements ErrorHandler {
        private final List<ApiError.CampoInvalido> errores = new ArrayList<>();

        @Override public void warning(SAXParseException e) { /* ignorar warnings */ }
        @Override public void error(SAXParseException e) { add(e); }
        @Override public void fatalError(SAXParseException e) { add(e); }

        private void add(SAXParseException e) {
            String ubicacion = "linea " + e.getLineNumber() + ", col " + e.getColumnNumber();
            errores.add(new ApiError.CampoInvalido(ubicacion, e.getMessage()));
        }
    }
}
