package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Modelo JAXB del DTE de la familia factura/notas (33/34/56/61), alineado al
 * esquema oficial {@code DTE_v10.xsd} (namespace SiiDte via package-info):
 * orden de elementos del XSD (en particular {@code IndExe} ANTES de
 * {@code NmbItem}), {@code Acteco} obligatorio del Emisor y {@code TmstFirma}
 * al cierre del Documento.
 *
 * El TED va como subarbol DOM ({@code @XmlAnyElement}): lo produce
 * {@link TedGenerator} ya aplanado y firmado, y debe conservarse byte-identico
 * dentro del documento (la boleta 39/41 usa {@link ModeloBoleta}).
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
        // Orden del XSD: Detalle*, DscRcgGlobal*, Referencia*, TED, TmstFirma.
        @XmlElement(name = "DscRcgGlobal") public List<DscRcgGlobal> dscRcgGlobal;
        @XmlElement(name = "Referencia") public List<Referencia> referencias;
        /** TED aplanado y firmado, insertado como DOM para no re-serializarlo. */
        @XmlAnyElement public Element ted;
        @XmlElement(name = "TmstFirma") public String tmstFirma;
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
        /** Codigo de actividad economica: obligatorio en el XSD oficial (1..4). */
        @XmlElement(name = "Acteco") public Integer acteco;
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
        // Nullables: un documento sin monto afecto (34, nota 100% exenta) debe
        // OMITIR MntNeto/TasaIVA/IVA — el SII rechaza una exenta que declare IVA.
        @XmlElement(name = "MntNeto") public Long neto;
        @XmlElement(name = "MntExe") public long exento;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "TasaIVA") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double tasaIva;
        @XmlElement(name = "IVA") public Long iva;
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
        // Orden del XSD oficial: IndExe va ANTES de NmbItem (campo 5 vs 8).
        @XmlElement(name = "IndExe") public Integer indicadorExento;
        @XmlElement(name = "NmbItem") public String nombre;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "QtyItem") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double cantidad;
        @XmlElement(name = "UnmdItem") public String unidad;
        // Dec12_6Type exige minimo 0.000001: con precio 0 el elemento se OMITE (null).
        @XmlElement(name = "PrcItem") public Long precioUnitario;
        // PctType (0.01-999.99): sin porcentaje el elemento se OMITE (null). Va
        // ANTES de DescuentoMonto en el orden del XSD.
        @XmlElement(name = "DescuentoPct") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double descuentoPct;
        // MntImpType exige minimo 1: sin descuento el elemento se OMITE (null).
        @XmlElement(name = "DescuentoMonto") public Long descuento;
        // Codigo del otro impuesto de la linea: va ANTES de MontoItem. Null -> omitido.
        @XmlElement(name = "CodImpAdic") public Integer codImpAdic;
        @XmlElement(name = "MontoItem") public long montoItem;
    }

    /**
     * Descuento o recargo global del documento (DscRcgGlobal). Sin IndExeDR el
     * D/R aplica sobre el monto afecto/neto, que es el unico caso soportado.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DscRcgGlobal {
        @XmlElement(name = "NroLinDR") public int numeroLinea;
        /** "D" descuento, "R" recargo. */
        @XmlElement(name = "TpoMov") public String tipoMovimiento;
        /** "%" porcentaje, "$" pesos. */
        @XmlElement(name = "TpoValor") public String tipoValor;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "ValorDR") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double valor;
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
}
