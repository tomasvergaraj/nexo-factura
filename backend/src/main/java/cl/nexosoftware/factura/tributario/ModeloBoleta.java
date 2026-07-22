package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Modelo JAXB del DTE de boleta (39/41), alineado al esquema oficial
 * {@code EnvioBOLETA_v11.xsd} — que es un esquema DISTINTO al de factura:
 * <ul>
 *   <li>Emisor con {@code RznSocEmisor}/{@code GiroEmisor} (no RznSoc/GiroEmis)
 *       y sin {@code Acteco};</li>
 *   <li>{@code IndServicio} obligatorio en IdDoc (3 = boleta de ventas y
 *       servicios, el caso de este sistema);</li>
 *   <li>Totales sin {@code TasaIVA} ni {@code ImptoReten}; MntNeto/MntExe/IVA
 *       opcionales (se emiten solo los que aplican) y MntTotal obligatorio;</li>
 *   <li>{@code TmstFirma} obligatorio al cierre del Documento.</li>
 * </ul>
 * El TED va como subarbol DOM, igual que en {@link ModeloDte}.
 */
public final class ModeloBoleta {

    private ModeloBoleta() {}

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
        /** 3 = Boleta de Ventas y Servicio (enum 1..4 del esquema). */
        @XmlElement(name = "IndServicio") public int indServicio = 3;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Emisor {
        @XmlElement(name = "RUTEmisor") public String rut;
        @XmlElement(name = "RznSocEmisor") public String razonSocial;
        @XmlElement(name = "GiroEmisor") public String giro;
        @XmlElement(name = "DirOrigen") public String direccion;
        @XmlElement(name = "CmnaOrigen") public String comuna;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Receptor {
        @XmlElement(name = "RUTRecep") public String rut;
        @XmlElement(name = "RznSocRecep") public String razonSocial;
        @XmlElement(name = "DirRecep") public String direccion;
        @XmlElement(name = "CmnaRecep") public String comuna;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Totales {
        // Nullables: en la boleta afecta van MntNeto+IVA; en la exenta, MntExe.
        @XmlElement(name = "MntNeto") public Long neto;
        @XmlElement(name = "MntExe") public Long exento;
        @XmlElement(name = "IVA") public Long iva;
        @XmlElement(name = "MntTotal") public long total;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "NroLinDet") public int numeroLinea;
        // Orden del esquema de boleta: IndExe ANTES de NmbItem.
        @XmlElement(name = "IndExe") public Integer indicadorExento;
        @XmlElement(name = "NmbItem") public String nombre;
        // Decimal plano (sin notacion cientifica) para cumplir xs:decimal.
        @XmlElement(name = "QtyItem") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double cantidad;
        @XmlElement(name = "UnmdItem") public String unidad;
        // Dec5Type exige minimo 0.000001: con precio 0 el elemento se OMITE (null).
        @XmlElement(name = "PrcItem") public Long precioUnitario;
        // Sin descuento el elemento se omite (coherente con la rama factura).
        // PctType: sin porcentaje se OMITE (null); va ANTES de DescuentoMonto.
        @XmlElement(name = "DescuentoPct") @XmlJavaTypeAdapter(PlainDecimalAdapter.class) public Double descuentoPct;
        @XmlElement(name = "DescuentoMonto") public Long descuento;
        /** MontoItem de boleta: monto BRUTO de la linea (IVA incluido). */
        @XmlElement(name = "MontoItem") public long montoItem;
    }
}
