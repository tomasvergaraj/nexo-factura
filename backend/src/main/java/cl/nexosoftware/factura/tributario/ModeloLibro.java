package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.util.List;

/**
 * Modelo JAXB del libro de compra/venta (IECV) alineado al esquema OFICIAL
 * {@code LibroCV_v10.xsd} del SII (namespace SiiDte via package-info): caratula
 * completa (RutEnvia, FchResol/NroResol, TipoLibro/TipoEnvio/FolioNotificacion),
 * ResumenPeriodo con IVA uso comun (FctProp/TotCredIVAUsoComun), IVA no
 * recuperable y otros impuestos, Detalle por documento y TmstFirma. El atributo
 * ID del EnvioLibro es la referencia de la firma XMLDSig enveloped.
 *
 * El orden de los campos replica el orden del XSD (JAXB marshalla en orden de
 * declaracion); los opcionales son wrappers nulos para omitirse del XML.
 */
public final class ModeloLibro {

    private ModeloLibro() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "LibroCompraVenta")
    public static class LibroCompraVenta {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "EnvioLibro") public EnvioLibro envioLibro;
    }

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
        @XmlElement(name = "PeriodoTributario") public String periodoTributario; // AAAA-MM
        @XmlElement(name = "FchResol") public String fchResol;                   // AAAA-MM-DD
        @XmlElement(name = "NroResol") public int nroResol;
        @XmlElement(name = "TipoOperacion") public String tipoOperacion;         // VENTA | COMPRA
        @XmlElement(name = "TipoLibro") public String tipoLibro;                 // MENSUAL | ESPECIAL | RECTIFICA
        @XmlElement(name = "TipoEnvio") public String tipoEnvio;                 // TOTAL | PARCIAL | FINAL | AJUSTE
        /** Folio de la notificacion del SII que solicita un libro ESPECIAL. */
        @XmlElement(name = "FolioNotificacion") public Long folioNotificacion;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ResumenPeriodo {
        @XmlElement(name = "TotalesPeriodo") public List<TotalesPeriodo> totalesPeriodo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotalesPeriodo {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        // 1 = IVA. El Formato IECV lo hace OBLIGATORIO en el libro de compras
        // (el XSD lo declara opcional, pero el validador del SII rechaza sin el).
        @XmlElement(name = "TpoImp") public Integer tpoImp;
        @XmlElement(name = "TotDoc") public long totDoc;
        @XmlElement(name = "TotAnulado") public Long totAnulado;
        // Numeros de operaciones exentas / con IVA recuperable (LC).
        @XmlElement(name = "TotOpExe") public Long totOpExe;
        @XmlElement(name = "TotMntExe") public long totMntExe;
        @XmlElement(name = "TotMntNeto") public long totMntNeto;
        @XmlElement(name = "TotOpIVARec") public Long totOpIvaRec;
        @XmlElement(name = "TotMntIVA") public long totMntIva;
        // IVA no recuperable por codigo (LC): antes del uso comun en el XSD.
        @XmlElement(name = "TotIVANoRec") public List<TotIvaNoRec> totIvaNoRec;
        @XmlElement(name = "TotOpIVAUsoComun") public Long totOpIvaUsoComun;
        @XmlElement(name = "TotIVAUsoComun") public Long totIvaUsoComun;
        // Factor de proporcionalidad del IVA uso comun (LC). String ya formateado
        // con DOS decimales ("0.60"): el validador del SII rechaza "0.6" (LRS).
        @XmlElement(name = "FctProp") public String fctProp;
        @XmlElement(name = "TotCredIVAUsoComun") public Long totCredIvaUsoComun;
        // Otros impuestos agregados por codigo (adicionales y retenciones).
        @XmlElement(name = "TotOtrosImp") public List<TotOtroImp> totOtrosImp;
        @XmlElement(name = "TotOpIVARetTotal") public Long totOpIvaRetTotal;
        @XmlElement(name = "TotIVARetTotal") public Long totIvaRetTotal;
        @XmlElement(name = "TotMntTotal") public long totMntTotal;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotIvaNoRec {
        @XmlElement(name = "CodIVANoRec") public int codIvaNoRec;
        @XmlElement(name = "TotOpIVANoRec") public Long totOpIvaNoRec;
        @XmlElement(name = "TotMntIVANoRec") public long totMntIvaNoRec;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotOtroImp {
        @XmlElement(name = "CodImp") public int codImp;
        @XmlElement(name = "TotMntImp") public long totMntImp;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        @XmlElement(name = "NroDoc") public long nroDoc;
        @XmlElement(name = "Anulado") public String anulado; // "A" si esta anulado
        @XmlElement(name = "TpoImp") public Integer tpoImp;  // 1 = IVA (LC)
        // Tasa del IVA de la operacion (LC): el SII repara las filas afectas sin ella.
        @XmlElement(name = "TasaImp") public String tasaImp;
        @XmlElement(name = "FchDoc") public String fchDoc;   // AAAA-MM-DD
        @XmlElement(name = "RUTDoc") public String rutDoc;
        @XmlElement(name = "RznSoc") public String rznSoc;
        @XmlElement(name = "MntExe") public Long mntExe;
        @XmlElement(name = "MntNeto") public Long mntNeto;
        @XmlElement(name = "MntIVA") public Long mntIva;
        // IVA no recuperable del documento (LC), con su codigo.
        @XmlElement(name = "IVANoRec") public List<IvaNoRec> ivaNoRec;
        // IVA de uso comun del documento (LC).
        @XmlElement(name = "IVAUsoComun") public Long ivaUsoComun;
        // Otros impuestos del documento (codigo, tasa, monto).
        @XmlElement(name = "OtrosImp") public List<OtroImp> otrosImp;
        // IVA retenido total (cambio de sujeto), en el libro de VENTAS.
        @XmlElement(name = "IVARetTotal") public Long ivaRetTotal;
        @XmlElement(name = "MntTotal") public Long mntTotal;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IvaNoRec {
        @XmlElement(name = "CodIVANoRec") public int codIvaNoRec;
        @XmlElement(name = "MntIVANoRec") public long mntIvaNoRec;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OtroImp {
        @XmlElement(name = "CodImp") public int codImp;
        // String ya formateado ("19" o "20.5"): sin ".0" colgante para el
        // validador legado del IECV.
        @XmlElement(name = "TasaImp") public String tasaImp;
        @XmlElement(name = "MntImp") public long mntImp;
    }
}
