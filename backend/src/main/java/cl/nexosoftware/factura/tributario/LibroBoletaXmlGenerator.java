package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.empresa.Empresa;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el XML {@code LibroBoleta} (Libro de Boletas Electronicas) alineado
 * al esquema OFICIAL {@code LibroBOLETA_v10.xsd}, listo para firmarse (XMLDSig
 * enveloped con Reference al atributo ID del EnvioLibro).
 *
 * Es el envio 4 del set de pruebas de certificacion de boletas ("Libro de
 * boletas electronicas (XML) asociado a las boletas electronicas del set"), y
 * por eso el esquema solo admite {@code TipoLibro=ESPECIAL} con el
 * {@code FolioNotificacion} de la notificacion del SII. No se sube al SII: el
 * set lo pide como ADJUNTO del correo a SII_BE_Certificacion@sii.cl.
 *
 * <p><b>Coherencia con el RCOF.</b> Los dos documentos describen los mismos
 * folios del mismo periodo y el SII los cruza, asi que usan la misma regla: un
 * folio cuyo documento no es valido ({@code EstadoDte.folioSinDocumentoValido})
 * se informa con {@code Anulado=A}, se cuenta en {@code TotDoc} y en
 * {@code TotAnulado}, y NO suma montos. Asi {@code TotDoc} calza con
 * FoliosEmitidos del RCOF y {@code TotAnulado} con FoliosAnulados.
 */
@Component
public class LibroBoletaXmlGenerator {

    public static final String ID_ENVIO_LIBRO_BOLETA = "NexoLibroBol";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /** 3 = Venta y Servicio, el mismo IndServicio que declara el DTE de boleta. */
    private static final int TPO_SERV_VENTA_Y_SERVICIO = 3;

    private final Clock clock;

    public LibroBoletaXmlGenerator() {
        this(Clock.system(ZoneId.of("America/Santiago")));
    }

    LibroBoletaXmlGenerator(Clock clock) {
        this.clock = clock;
    }

    /** Una boleta del periodo, ya resuelta a lo que el libro informa de ella. */
    public record BoletaLibro(int tipoDocumento, long folio, LocalDate fechaEmision,
                              boolean anulada, long neto, long iva, long exento, long total,
                              double tasaIva) {}

    /** Datos de caratula que no salen de las boletas del periodo. */
    public record CaratulaLibroBoleta(String rutEnvia, String fchResol, int nroResol,
                                      long folioNotificacion) {}

    public String generar(YearMonth periodo, List<BoletaLibro> boletas, Empresa emisor,
                          CaratulaLibroBoleta caratula) {
        ModeloLibroBoleta.LibroBoleta libro = new ModeloLibroBoleta.LibroBoleta();
        ModeloLibroBoleta.EnvioLibro envio = new ModeloLibroBoleta.EnvioLibro();
        envio.id = ID_ENVIO_LIBRO_BOLETA;
        libro.envioLibro = envio;

        ModeloLibroBoleta.Caratula car = new ModeloLibroBoleta.Caratula();
        car.rutEmisorLibro = emisor.getRut();
        car.rutEnvia = caratula.rutEnvia();
        car.periodoTributario = periodo.toString();
        car.fchResol = caratula.fchResol();
        car.nroResol = caratula.nroResol();
        car.folioNotificacion = caratula.folioNotificacion();
        envio.caratula = car;

        envio.resumenPeriodo = resumir(boletas);
        envio.detalle = boletas.stream().map(this::aDetalle).toList();
        envio.tmstFirma = LocalDateTime.now(clock).format(TIMESTAMP);

        String xml = JaxbXml.marshal(libro, "No se pudo generar el XML del libro de boletas");
        // xsi:schemaLocation como en el resto de los archivos que ven los
        // validadores del SII: el upload identifica el tipo por esta declaracion
        // (sin ella: "SCH-00001: Invalid Schema Name", hallado con el RCOF del
        // set de boletas). Este libro va adjunto al correo, pero el revisor lo
        // pasa por el mismo validador.
        return xml.replaceFirst("<LibroBoleta ",
                "<LibroBoleta xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"http://www.sii.cl/SiiDte LibroBOLETA_v10.xsd\" ");
    }

    private ModeloLibroBoleta.Detalle aDetalle(BoletaLibro b) {
        ModeloLibroBoleta.Detalle d = new ModeloLibroBoleta.Detalle();
        d.tpoDoc = b.tipoDocumento();
        d.folioDoc = b.folio();
        d.anulado = b.anulada() ? "A" : null;
        d.tpoServ = TPO_SERV_VENTA_Y_SERVICIO;
        d.fchEmiDoc = b.fechaEmision().format(FECHA);
        // Un folio anulado se reporta SIN montos (igual que en el RCOF).
        d.mntExe = !b.anulada() && b.exento() > 0 ? b.exento() : null;
        d.mntTotal = b.anulada() ? 0L : b.total();
        return d;
    }

    /**
     * Un TotalesPeriodo por tipo de boleta presente, en orden de codigo. Un tipo
     * sin boletas NO se informa: el esquema declara {@code TotDoc} como
     * positiveInteger, asi que un total en cero seria invalido.
     */
    private ModeloLibroBoleta.ResumenPeriodo resumir(List<BoletaLibro> boletas) {
        Map<Integer, List<BoletaLibro>> porTipo = new LinkedHashMap<>();
        boletas.stream()
                .sorted(java.util.Comparator.comparingInt(BoletaLibro::tipoDocumento))
                .forEach(b -> porTipo.computeIfAbsent(b.tipoDocumento(), t -> new ArrayList<>()).add(b));

        List<ModeloLibroBoleta.TotalesPeriodo> totales = new ArrayList<>();
        for (var entrada : porTipo.entrySet()) {
            List<BoletaLibro> delTipo = entrada.getValue();
            List<BoletaLibro> vigentes = delTipo.stream().filter(b -> !b.anulada()).toList();
            long anuladas = delTipo.size() - vigentes.size();

            ModeloLibroBoleta.TotalesServicio ts = new ModeloLibroBoleta.TotalesServicio();
            ts.totDoc = delTipo.size();
            ts.totMntNeto = vigentes.stream().mapToLong(BoletaLibro::neto).sum();
            ts.totMntIva = vigentes.stream().mapToLong(BoletaLibro::iva).sum();
            ts.totMntTotal = vigentes.stream().mapToLong(BoletaLibro::total).sum();
            long exento = vigentes.stream().mapToLong(BoletaLibro::exento).sum();
            ts.totMntExe = exento > 0 ? exento : null;
            // La tasa solo tiene sentido donde hay IVA (la boleta exenta no lo
            // lleva) y sale de los documentos, no de una constante: la tasa
            // vigente se guarda al emitir y puede cambiar por ley.
            ts.tasaIva = ts.totMntIva > 0
                    ? vigentes.stream().mapToDouble(BoletaLibro::tasaIva).max().orElse(0)
                    : null;

            ModeloLibroBoleta.TotalesPeriodo tp = new ModeloLibroBoleta.TotalesPeriodo();
            tp.tpoDoc = entrada.getKey();
            tp.totAnulado = anuladas > 0 ? anuladas : null;
            tp.totalesServicio = List.of(ts);
            totales.add(tp);
        }

        ModeloLibroBoleta.ResumenPeriodo resumen = new ModeloLibroBoleta.ResumenPeriodo();
        resumen.totalesPeriodo = totales;
        return resumen;
    }
}
