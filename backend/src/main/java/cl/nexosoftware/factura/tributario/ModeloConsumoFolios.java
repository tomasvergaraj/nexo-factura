package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del Reporte de Consumo de Folios (ConsumoFolios) del SII, subconjunto
 * representativo. Reproduce Caratula + un Resumen por tipo de boleta con los folios
 * utilizados/anulados y sus rangos. NO se firma ni se envia al SII (requiere
 * certificado y secuencia de envio real), igual que el resto del flujo tributario.
 */
public final class ModeloConsumoFolios {

    private ModeloConsumoFolios() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "ConsumoFolios")
    public static class ConsumoFolios {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "Caratula") public Caratula caratula;
        @XmlElement(name = "Resumen") public List<Resumen> resumen;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Caratula {
        @XmlElement(name = "RutEmisor") public String rutEmisor;
        @XmlElement(name = "FchInicio") public String fchInicio;
        @XmlElement(name = "FchFinal") public String fchFinal;
        @XmlElement(name = "SecEnvio") public int secEnvio;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Resumen {
        @XmlElement(name = "TipoDocumento") public int tipoDocumento;
        @XmlElement(name = "MntNeto") public long mntNeto;
        @XmlElement(name = "MntIva") public long mntIva;
        @XmlElement(name = "MntExento") public Long mntExento;
        @XmlElement(name = "MntTotal") public long mntTotal;
        @XmlElement(name = "FoliosEmitidos") public long foliosEmitidos;
        @XmlElement(name = "FoliosAnulados") public long foliosAnulados;
        @XmlElement(name = "FoliosUtilizados") public long foliosUtilizados;
        @XmlElement(name = "RangoUtilizados") public Rango rangoUtilizados;
        @XmlElement(name = "RangoAnulados") public Rango rangoAnulados;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Rango {
        @XmlElement(name = "Inicial") public long inicial;
        @XmlElement(name = "Final") public long fin;
    }
}
