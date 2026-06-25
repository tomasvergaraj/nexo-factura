package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Genera el Timbre Electronico (TED) del DTE.
 *
 * Arma el bloque DD con los datos que el SII exige para el timbre. La firma del
 * timbre (FRMT) debe calcularse con la llave privada incluida en el CAF
 * (RSA-SHA1 sobre el DD). Aqui se entrega un valor de marcador de posicion: al
 * integrar el CAF real, reemplazar {@link #firmarDd} por la firma efectiva.
 */
@Component
public class TedGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    public ModeloDte.Ted generar(DocumentoTributario doc, String rutEmisor) {
        ModeloDte.Dd dd = new ModeloDte.Dd();
        dd.rutEmisor = rutEmisor;
        dd.tipoDte = doc.getTipoDte().getCodigo();
        dd.folio = doc.getFolio();
        dd.fechaEmision = doc.getFechaEmision().format(FECHA);
        dd.rutReceptor = doc.getReceptorRut();
        dd.razonSocialReceptor = abreviar(doc.getReceptorRazonSocial(), 40);
        dd.monto = doc.getTotal();
        dd.primerItem = doc.getLineas().isEmpty() ? "" : abreviar(doc.getLineas().get(0).getNombre(), 40);
        dd.timestamp = doc.getCreadoEn().toLocalDateTime().toString();

        ModeloDte.Ted ted = new ModeloDte.Ted();
        ted.dd = dd;
        ModeloDte.Frmt frmt = new ModeloDte.Frmt();
        frmt.valor = firmarDd(dd);
        ted.frmt = frmt;
        return ted;
    }

    /**
     * Serializa el TED a su fragmento XML ({@code <TED ...>...</TED>}) en ISO-8859-1,
     * sin formato. Es el contenido que se codifica en el PDF417 del timbre.
     */
    public String aXml(ModeloDte.Ted ted) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(ModeloDte.Ted.class);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1");
            StringWriter sw = new StringWriter();
            marshaller.marshal(ted, sw);
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el TED a XML", e);
        }
    }

    private String firmarDd(ModeloDte.Dd dd) {
        // Placeholder: en produccion se firma con la llave privada del CAF (RSA-SHA1).
        String semilla = dd.rutEmisor + "|" + dd.tipoDte + "|" + dd.folio + "|" + dd.monto;
        return Base64.getEncoder().encodeToString(("FRMT-PENDIENTE-CAF:" + semilla).getBytes());
    }

    private String abreviar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
