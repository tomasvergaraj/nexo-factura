package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

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
        // El orden de los campos es el orden de marshalling: Detalle*, Referencia*, TED.
        @XmlElement(name = "Referencia") public List<Referencia> referencias;
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
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "TasaIVA") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double tasaIva;
        @XmlElement(name = "IVA") public long iva;
        // Otros impuestos (adicionales y retenciones): bloque repetible, DESPUES de
        // IVA y ANTES de MntTotal. Null/vacio -> JAXB no emite ningun nodo.
        @XmlElement(name = "ImptoReten") public List<ImptoReten> imptoReten;
        @XmlElement(name = "MntTotal") public long total;
    }

    /**
     * Bloque de otro impuesto en Totales (TipoImp/TasaImp/MontoImp). Representa tanto
     * impuestos adicionales como la retencion de IVA; el signo en MntTotal lo decide
     * el catalogo, no el XML (el DTE no tiene IVARetTotal).
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ImptoReten {
        @XmlElement(name = "TipoImp") public int tipoImp;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "TasaImp") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double tasaImp;
        @XmlElement(name = "MontoImp") public long montoImp;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "NroLinDet") public int numeroLinea;
        @XmlElement(name = "NmbItem") public String nombre;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "QtyItem") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double cantidad;
        @XmlElement(name = "UnmdItem") public String unidad;
        @XmlElement(name = "PrcItem") public long precioUnitario;
        @XmlElement(name = "DescuentoMonto") public long descuento;
        @XmlElement(name = "IndExe") public Integer indicadorExento;
        // Codigo del otro impuesto de la linea: va ANTES de MontoItem (campo 37 vs 38
        // del esquema oficial). Null -> JAXB lo omite.
        @XmlElement(name = "CodImpAdic") public Integer codImpAdic;
        @XmlElement(name = "MontoItem") public long montoItem;
    }

    /** Referencia a otro documento (obligatoria en notas 56/61). */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Referencia {
        @XmlElement(name = "NroLinRef") public int numeroLinea;
        @XmlElement(name = "TpoDocRef") public int tipoDocumentoRef;
        @XmlElement(name = "FolioRef") public long folioRef;
        @XmlElement(name = "FchRef") public String fechaRef;
        @XmlElement(name = "CodRef") public int codigoReferencia;
        @XmlElement(name = "RazonRef") public String razon;
    }

    /** Timbre Electronico (TED). En produccion el campo FRMT se firma con la llave del CAF. */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "TED")
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
