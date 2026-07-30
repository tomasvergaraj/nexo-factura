package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del Libro de Boletas Electronicas, alineado al esquema OFICIAL
 * {@code LibroBOLETA_v10.xsd}. Es un documento DISTINTO del libro IECV
 * ({@link ModeloLibro}, LibroCV_v10) aunque comparta vocabulario:
 * <ul>
 *   <li>raiz {@code LibroBoleta} con un {@code EnvioLibro} (atributo ID, destino
 *       de la Reference de la firma);</li>
 *   <li>{@code TipoLibro} solo admite {@code ESPECIAL} y {@code FolioNotificacion}
 *       es OBLIGATORIO: este libro existe unicamente como respuesta a una
 *       notificacion del SII —el numero de atencion del set de pruebas—;</li>
 *   <li>los montos neto e IVA van SOLO en el resumen del periodo
 *       ({@code TotMntNeto} y {@code TotMntIVA} son obligatorios ahi), no en el
 *       del segmento;</li>
 *   <li>{@code DoctoType} solo acepta 39 y 41: las notas de credito no entran.</li>
 * </ul>
 */
public final class ModeloLibroBoleta {

    private ModeloLibroBoleta() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "LibroBoleta")
    public static class LibroBoleta {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "EnvioLibro") public EnvioLibro envioLibro;
    }

    /** Orden del XSD: Caratula, ResumenSegmento?, ResumenPeriodo?, Detalle*, TmstFirma. */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class EnvioLibro {
        @XmlAttribute(name = "ID") public String id;
        @XmlElement(name = "Caratula") public Caratula caratula;
        @XmlElement(name = "ResumenPeriodo") public ResumenPeriodo resumenPeriodo;
        @XmlElement(name = "Detalle") public List<Detalle> detalle;
        @XmlElement(name = "TmstFirma") public String tmstFirma;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Caratula {
        @XmlElement(name = "RutEmisorLibro") public String rutEmisorLibro;
        @XmlElement(name = "RutEnvia") public String rutEnvia;
        /** AAAA-MM (xs:gYearMonth). */
        @XmlElement(name = "PeriodoTributario") public String periodoTributario;
        @XmlElement(name = "FchResol") public String fchResol;
        @XmlElement(name = "NroResol") public int nroResol;
        /** El esquema no admite otro valor. */
        @XmlElement(name = "TipoLibro") public String tipoLibro = "ESPECIAL";
        @XmlElement(name = "TipoEnvio") public String tipoEnvio = "TOTAL";
        @XmlElement(name = "NroSegmento") public Integer nroSegmento;
        /** Numero de la notificacion con que el SII solicita el libro. */
        @XmlElement(name = "FolioNotificacion") public long folioNotificacion;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ResumenPeriodo {
        /** Uno por tipo de boleta presente (maxOccurs 2 en el esquema). */
        @XmlElement(name = "TotalesPeriodo") public List<TotalesPeriodo> totalesPeriodo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotalesPeriodo {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        /** positiveInteger en el esquema: se OMITE cuando no hay anulados. */
        @XmlElement(name = "TotAnulado") public Long totAnulado;
        @XmlElement(name = "TotalesServicio") public List<TotalesServicio> totalesServicio;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotalesServicio {
        /** 3 = Venta y Servicio, coherente con el IndServicio del DTE de boleta. */
        @XmlElement(name = "TpoServ") public int tpoServ = 3;
        @XmlElement(name = "PeriodoDevengado") public String periodoDevengado;
        /** positiveInteger: un tipo sin documentos no se informa. */
        @XmlElement(name = "TotDoc") public long totDoc;
        @XmlElement(name = "TotMntExe") public Long totMntExe;
        @XmlElement(name = "TotMntNeto") public long totMntNeto;
        @XmlElement(name = "TasaIVA") public Double tasaIva;
        @XmlElement(name = "TotMntIVA") public long totMntIva;
        @XmlElement(name = "TotMntTotal") public long totMntTotal;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        @XmlElement(name = "FolioDoc") public long folioDoc;
        /** "A" si el folio se consumio sin documento valido; null si no. */
        @XmlElement(name = "Anulado") public String anulado;
        @XmlElement(name = "TpoServ") public Integer tpoServ;
        @XmlElement(name = "FchEmiDoc") public String fchEmiDoc;
        @XmlElement(name = "MntExe") public Long mntExe;
        @XmlElement(name = "MntTotal") public Long mntTotal;
    }
}
