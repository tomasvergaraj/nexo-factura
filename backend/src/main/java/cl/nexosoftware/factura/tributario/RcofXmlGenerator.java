package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofPorTipo;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el XML ConsumoFolios (RCOF) a partir del reporte agregado, usando JAXB.
 *
 * NO se firma ni se envia al SII: requiere certificado y una secuencia de envio
 * real. Solo materializa la estructura del reporte (coherente con el subconjunto
 * del esquema usado en {@link XmlDteGenerator}).
 */
@Component
public class RcofXmlGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    public String generar(RcofResponse reporte, Empresa emisor) {
        ModeloConsumoFolios.ConsumoFolios cf = new ModeloConsumoFolios.ConsumoFolios();

        ModeloConsumoFolios.Caratula car = new ModeloConsumoFolios.Caratula();
        car.rutEmisor = emisor.getRut();
        car.fchInicio = reporte.fecha().format(FECHA);
        car.fchFinal = reporte.fecha().format(FECHA);
        car.secEnvio = reporte.secEnvio();
        cf.caratula = car;

        List<ModeloConsumoFolios.Resumen> resumenes = new ArrayList<>();
        for (RcofPorTipo t : reporte.documentos()) {
            // El XML omite los tipos sin movimiento; el JSON los conserva con ceros.
            if (t.foliosEmitidos() == 0) {
                continue;
            }
            ModeloConsumoFolios.Resumen r = new ModeloConsumoFolios.Resumen();
            r.tipoDocumento = t.tipoDocumento();
            r.mntNeto = t.montoNeto();
            r.mntIva = t.montoIva();
            r.mntExento = t.montoExento() > 0 ? t.montoExento() : null;
            r.mntTotal = t.montoTotal();
            r.foliosEmitidos = t.foliosEmitidos();
            r.foliosAnulados = t.foliosAnulados();
            r.foliosUtilizados = t.foliosUtilizados();
            if (t.foliosUtilizados() > 0) {
                r.rangoUtilizados = rango(t.folioInicial(), t.folioFinal());
            }
            if (t.foliosAnulados() > 0) {
                r.rangoAnulados = rango(t.folioAnuladoInicial(), t.folioAnuladoFinal());
            }
            resumenes.add(r);
        }
        cf.resumen = resumenes;

        return marshal(cf);
    }

    private ModeloConsumoFolios.Rango rango(Long inicial, Long fin) {
        ModeloConsumoFolios.Rango r = new ModeloConsumoFolios.Rango();
        r.inicial = inicial != null ? inicial : 0;
        r.fin = fin != null ? fin : 0;
        return r;
    }

    private String marshal(ModeloConsumoFolios.ConsumoFolios cf) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(ModeloConsumoFolios.ConsumoFolios.class);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter sw = new StringWriter();
            marshaller.marshal(cf, sw);
            return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + sw;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el XML del RCOF", e);
        }
    }
}
