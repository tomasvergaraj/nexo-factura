package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import org.springframework.stereotype.Component;

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
