package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ApiError;
import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marshalling JAXB compartido por los generadores de documentos SII con el
 * prologo ISO-8859-1 canonico. Dos variantes:
 * <ul>
 *   <li>{@link #marshal}: identado, para documentos legibles que no se firman
 *       byte a byte (RCOF, libros IECV).</li>
 *   <li>{@link #marshalPlano}: sin formato (una sola linea), para el DTE — el
 *       timbre (TED) embebido debe conservarse byte-identico a su forma firmada
 *       y el documento no debe reformatearse nunca despues de firmar.</li>
 * </ul>
 * El JAXBContext es thread-safe y costoso de construir, asi que se cachea por
 * clase raiz; el Marshaller no es thread-safe y se crea por llamada.
 */
final class JaxbXml {

    static final String PROLOGO = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n";

    private static final Map<Class<?>, JAXBContext> CONTEXTOS = new ConcurrentHashMap<>();

    private JaxbXml() {}

    /** Marshalla identado (documentos que no se firman byte a byte). */
    static String marshal(Object raiz, String mensajeError) {
        return marshal(raiz, mensajeError, true);
    }

    /** Marshalla sin formato: todo el documento en una sola linea tras el prologo. */
    static String marshalPlano(Object raiz, String mensajeError) {
        return marshal(raiz, mensajeError, false);
    }

    private static String marshal(Object raiz, String mensajeError, boolean formateado) {
        try {
            JAXBContext ctx = CONTEXTOS.computeIfAbsent(raiz.getClass(), JaxbXml::crearContexto);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, formateado);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter sw = new StringWriter();
            marshaller.marshal(raiz, sw);
            return PROLOGO + sw;
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

    /**
     * Rechaza texto no representable en ISO-8859-1. Sin este guard,
     * {@code String.getBytes(ISO_8859_1)} degrada en silencio cada caracter no
     * mapeable a {@code ?} (em-dash, comillas tipograficas, emoji) y ese {@code ?}
     * queda firmado en el documento legal, el TED y el PDF417. Lanza
     * {@link DteInvalidoException} (422, revierte el folio) listando los
     * caracteres ofensivos para que el usuario corrija el dato de origen.
     */
    static void exigirLatin1(String texto, String contexto) {
        if (texto == null || StandardCharsets.ISO_8859_1.newEncoder().canEncode(texto)) {
            return;
        }
        Set<String> ofensivos = new LinkedHashSet<>();
        texto.codePoints()
                .filter(cp -> cp > 0xFF)
                .limit(20)
                .forEach(cp -> ofensivos.add("'" + new String(Character.toChars(cp)) + "' (U+"
                        + Integer.toHexString(cp).toUpperCase() + ")"));
        throw new DteInvalidoException(
                "Hay caracteres que no existen en ISO-8859-1 (el encoding que exige el SII) en "
                        + contexto + ": " + String.join(", ", ofensivos)
                        + ". Reemplacelos (p.ej. comillas tipograficas o guiones largos pegados desde Word).",
                List.of(new ApiError.CampoInvalido(contexto, "caracteres fuera de ISO-8859-1")));
    }
}
