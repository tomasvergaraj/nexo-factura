package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ApiError;
import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
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
 * Valida el XML del DTE contra el esquema XSD representativo ANTES de firmarlo.
 *
 * Thread-safety: el {@link Schema} se compila una sola vez en el constructor y es
 * inmutable/thread-safe; el {@link Validator} NO lo es, por lo que se crea uno
 * nuevo por cada llamada a {@link #validar(String)}.
 *
 * Seguridad (XXE): la SchemaFactory y cada Validator se endurecen
 * (FEATURE_SECURE_PROCESSING + ACCESS_EXTERNAL_DTD/SCHEMA vacios), de modo que un
 * XML con DOCTYPE o entidades externas no pueda leer recursos del host.
 */
@Component
public class DteXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(DteXmlValidator.class);
    private static final String XSD_CLASSPATH = "sii/DTE.xsd";

    private final Schema schema;
    private final boolean habilitado;

    public DteXmlValidator(@Value("${app.dte.validar-xsd:true}") boolean habilitado) {
        this.habilitado = habilitado;
        this.schema = compilarSchema();
        if (!habilitado) {
            log.warn("Validacion XSD del DTE DESHABILITADA (app.dte.validar-xsd=false). "
                    + "Solo para diagnostico, nunca en produccion.");
        }
    }

    private Schema compilarSchema() {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo endurecer la SchemaFactory XSD del DTE", e);
        }

        ClassPathResource recurso = new ClassPathResource(XSD_CLASSPATH);
        if (!recurso.exists()) {
            throw new IllegalStateException(
                    "No se encontro el esquema XSD del DTE en el classpath: " + XSD_CLASSPATH);
        }
        try (InputStream in = recurso.getInputStream()) {
            return factory.newSchema(new StreamSource(in));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo compilar el esquema XSD del DTE (" + XSD_CLASSPATH + ")", e);
        }
    }

    /**
     * Valida el XML completo contra el XSD, acumulando TODOS los errores.
     *
     * @param xml XML del DTE sin firmar (incluye la declaracion ISO-8859-1; se
     *            parsea via StringReader para ignorar el encoding declarado).
     * @throws DteInvalidoException si el XML no cumple el esquema.
     */
    public void validar(String xml) {
        if (!habilitado) {
            return;
        }
        Validator validator = schema.newValidator(); // por-llamada: Validator NO es thread-safe
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo endurecer el Validator XSD del DTE", e);
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
            throw new IllegalStateException("Error de E/S validando el XML del DTE", e);
        }

        if (!colector.errores.isEmpty()) {
            throw new DteInvalidoException(
                    "El XML del DTE generado no cumple el esquema XSD ("
                            + colector.errores.size() + " error(es))",
                    List.copyOf(colector.errores));
        }
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
