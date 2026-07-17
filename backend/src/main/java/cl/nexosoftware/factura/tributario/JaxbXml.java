package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marshalling JAXB compartido por los generadores de documentos SII (DTE, RCOF,
 * libros IECV): mismo formato (identado, sin declaracion propia) y mismo prologo
 * ISO-8859-1 prepend-eado. Centralizarlo evita que la politica de serializacion
 * derive entre documentos. El JAXBContext es thread-safe y costoso de construir,
 * asi que se cachea por clase raiz; el Marshaller no es thread-safe y se crea
 * por llamada.
 */
final class JaxbXml {

    private static final Map<Class<?>, JAXBContext> CONTEXTOS = new ConcurrentHashMap<>();

    private JaxbXml() {}

    /** Marshalla la raiz con el prologo ISO-8859-1 canonico de los documentos SII. */
    static String marshal(Object raiz, String mensajeError) {
        try {
            JAXBContext ctx = CONTEXTOS.computeIfAbsent(raiz.getClass(), JaxbXml::crearContexto);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter sw = new StringWriter();
            marshaller.marshal(raiz, sw);
            return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + sw;
        } catch (Exception e) {
            throw new IllegalStateException(mensajeError, e);
        }
    }

    private static JAXBContext crearContexto(Class<?> clase) {
        try {
            return JAXBContext.newInstance(clase);
        } catch (JAXBException e) {
            throw new IllegalStateException(
                    "No se pudo crear el contexto JAXB de " + clase.getSimpleName(), e);
        }
    }
}
