package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del DTE (subconjunto representativo del esquema del SII).
 *
 * Reproduce la estructura Documento -> Encabezado (IdDoc, Emisor, Receptor,
 * Totales) + Detalle + TED, suficiente para generar y firmar el XML. El esquema
 * oficial completo del SII agrega mas campos; este modelo concentra los que
 * intervienen en la emision de una factura o boleta tipica.
 */
public final class ModeloDte {

    private ModeloDte() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "DTE")
    public static class Dte {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "Documento") public Documento documento;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Documento {
        @XmlAttribute(name = "ID") public String id;
        @XmlElement(name = "Encabezado") public Encabezado encabezado;
        @XmlElement(name = "Detalle") public List<Detalle> detalle;
        @XmlElement(name = "TED") public Ted ted;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Encabezado {
        @XmlElement(name = "IdDoc") public IdDoc idDoc;
        @XmlElement(name = "Emisor") public Emisor emisor;
        @XmlElement(name = "Receptor") public Receptor receptor;
        @XmlElement(name = "Totales") public Totales totales;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IdDoc {
        @XmlElement(name = "TipoDTE") public int tipoDte;
        @XmlElement(name = "Folio") public long folio;
        @XmlElement(name = "FchEmis") public String fechaEmision;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Emisor {
        @XmlElement(name = "RUTEmisor") public String rut;
        @XmlElement(name = "RznSoc") public String razonSocial;
        @XmlElement(name = "GiroEmis") public String giro;
        @XmlElement(name = "DirOrigen") public String direccion;
        @XmlElement(name = "CmnaOrigen") public String comuna;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Receptor {
        @XmlElement(name = "RUTRecep") public String rut;
        @XmlElement(name = "RznSocRecep") public String razonSocial;
        @XmlElement(name = "GiroRecep") public String giro;
        @XmlElement(name = "DirRecep") public String direccion;
        @XmlElement(name = "CmnaRecep") public String comuna;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Totales {
        @XmlElement(name = "MntNeto") public long neto;
        @XmlElement(name = "MntExe") public long exento;
        @XmlElement(name = "TasaIVA") public double tasaIva;
        @XmlElement(name = "IVA") public long iva;
        @XmlElement(name = "MntTotal") public long total;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "NroLinDet") public int numeroLinea;
        @XmlElement(name = "NmbItem") public String nombre;
        @XmlElement(name = "QtyItem") public double cantidad;
        @XmlElement(name = "UnmdItem") public String unidad;
        @XmlElement(name = "PrcItem") public long precioUnitario;
        @XmlElement(name = "DescuentoMonto") public long descuento;
        @XmlElement(name = "IndExe") public Integer indicadorExento;
        @XmlElement(name = "MontoItem") public long montoItem;
    }

    /** Timbre Electronico (TED). En produccion el campo FRMT se firma con la llave del CAF. */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Ted {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "DD") public Dd dd;
        @XmlElement(name = "FRMT") public Frmt frmt;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Dd {
        @XmlElement(name = "RE") public String rutEmisor;
        @XmlElement(name = "TD") public int tipoDte;
        @XmlElement(name = "F") public long folio;
        @XmlElement(name = "FE") public String fechaEmision;
        @XmlElement(name = "RR") public String rutReceptor;
        @XmlElement(name = "RSR") public String razonSocialReceptor;
        @XmlElement(name = "MNT") public long monto;
        @XmlElement(name = "IT1") public String primerItem;
        @XmlElement(name = "TSTED") public String timestamp;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Frmt {
        @XmlAttribute(name = "algoritmo") public String algoritmo = "SHA1withRSA";
        @XmlValue public String valor;
    }
}
