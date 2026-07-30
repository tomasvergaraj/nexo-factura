package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofPorTipo;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el XML {@code ConsumoFolios} (RCOF) a partir del reporte agregado,
 * alineado al esquema OFICIAL {@code ConsumoFolio_v10.xsd} y listo para firmarse
 * (XMLDSig enveloped con Reference al atributo ID del DocumentoConsumoFolios).
 *
 * El archivo firmado ya no se sube al SII —la Res. Ex. SII N°53 de 2022 elimino
 * el envio del Consumo de Folios— pero sigue siendo el adjunto que pide el set
 * de pruebas de certificacion de boletas. Ver ROADMAP §15.
 */
@Component
public class RcofXmlGenerator {

    public static final String ID_CONSUMO_FOLIOS = "NexoRCOF";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Clock clock;

    public RcofXmlGenerator() {
        this(Clock.system(ZoneId.of("America/Santiago")));
    }

    RcofXmlGenerator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Datos de caratula que no salen del reporte del dia: quien envia, la
     * resolucion que lo autoriza y la secuencia. {@code secEnvio} arranca en 1 y
     * sube de a uno cada vez que se rehace el mismo dia (el SII lee un SecEnvio
     * mayor como correccion del anterior).
     */
    public record CaratulaRcof(String rutEnvia, String fchResol, int nroResol, int secEnvio) {

        /** Caratula de INSPECCION (sin certificado ni resolucion reales). */
        public static CaratulaRcof inspeccion(String rutEmisor, int secEnvio) {
            return new CaratulaRcof(rutEmisor, "2000-01-01", 0, secEnvio);
        }
    }

    public String generar(RcofResponse reporte, Empresa emisor, CaratulaRcof caratula) {
        ModeloConsumoFolios.ConsumoFolios cf = new ModeloConsumoFolios.ConsumoFolios();
        ModeloConsumoFolios.DocumentoConsumoFolios doc = new ModeloConsumoFolios.DocumentoConsumoFolios();
        doc.id = ID_CONSUMO_FOLIOS;
        cf.documento = doc;

        ModeloConsumoFolios.Caratula car = new ModeloConsumoFolios.Caratula();
        car.rutEmisor = emisor.getRut();
        car.rutEnvia = caratula.rutEnvia();
        car.fchResol = caratula.fchResol();
        car.nroResol = caratula.nroResol();
        car.fchInicio = reporte.fecha().format(FECHA);
        car.fchFinal = reporte.fecha().format(FECHA);
        car.secEnvio = caratula.secEnvio();
        car.tmstFirmaEnv = LocalDateTime.now(clock).format(TIMESTAMP);
        doc.caratula = car;

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
        doc.resumen = resumenes;

        return marshal(cf);
    }

    private ModeloConsumoFolios.Rango rango(Long inicial, Long fin) {
        ModeloConsumoFolios.Rango r = new ModeloConsumoFolios.Rango();
        r.inicial = inicial != null ? inicial : 0;
        r.fin = fin != null ? fin : 0;
        return r;
    }

    private String marshal(ModeloConsumoFolios.ConsumoFolios cf) {
        String xml = JaxbXml.marshal(cf, "No se pudo generar el XML del RCOF");
        // xsi:schemaLocation ANTES de firmar: el upload del SII identifica el tipo
        // de archivo por esta declaracion y sin ella rechaza con "SCH-00001:
        // Invalid Schema Name" (hallado al subir el RCOF del set de boletas por
        // el UPLOAD web de certificacion; mismo defecto que tuvo el IECV).
        return xml.replaceFirst("<ConsumoFolios ",
                "<ConsumoFolios xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"http://www.sii.cl/SiiDte ConsumoFolio_v10.xsd\" ");
    }
}
