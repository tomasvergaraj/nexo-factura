package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del Reporte de Consumo de Folios (ConsumoFolios) del SII, alineado
 * al esquema OFICIAL {@code ConsumoFolio_v10.xsd}: raiz {@code ConsumoFolios} con
 * un {@code DocumentoConsumoFolios} (Caratula + hasta 3 Resumen) y la firma como
 * ultimo hijo.
 *
 * El esquema exige el nodo {@code Signature}, asi que —igual que el DTE y el
 * libro IECV— el orden es generar → firmar enveloped (Reference al atributo
 * {@code ID} del DocumentoConsumoFolios) → validar.
 */
public final class ModeloConsumoFolios {

    private ModeloConsumoFolios() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "ConsumoFolios")
    public static class ConsumoFolios {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "DocumentoConsumoFolios") public DocumentoConsumoFolios documento;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DocumentoConsumoFolios {
        /** Obligatorio en el esquema: es el destino de la Reference de la firma. */
        @XmlAttribute(name = "ID") public String id;
        @XmlElement(name = "Caratula") public Caratula caratula;
        @XmlElement(name = "Resumen") public List<Resumen> resumen;
    }

    /** Orden de los elementos = el de la secuencia del XSD; JAXB emite por declaracion. */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Caratula {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "RutEmisor") public String rutEmisor;
        /** RUT que envia: el firmante del certificado (no siempre es el emisor). */
        @XmlElement(name = "RutEnvia") public String rutEnvia;
        @XmlElement(name = "FchResol") public String fchResol;
        @XmlElement(name = "NroResol") public int nroResol;
        @XmlElement(name = "FchInicio") public String fchInicio;
        @XmlElement(name = "FchFinal") public String fchFinal;
        /** Opcional; el SII asume 1 (correlativo dentro del dia). */
        @XmlElement(name = "Correlativo") public Integer correlativo;
        @XmlElement(name = "SecEnvio") public int secEnvio;
        @XmlElement(name = "TmstFirmaEnv") public String tmstFirmaEnv;
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
