package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del libro de compra/venta (LibroCompraVenta / IECV) del SII,
 * subconjunto representativo. Reproduce EnvioLibro con Caratula, un
 * TotalesPeriodo por tipo de documento y un Detalle por documento (las boletas
 * del libro de ventas van solo resumidas). Los documentos anulados llevan
 * {@code <Anulado>A</Anulado>} y montos en cero. NO se firma ni se envia al SII
 * (requiere certificado real), igual que el DTE y el RCOF.
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
        @XmlElement(name = "Caratula") public Caratula caratula;
        @XmlElement(name = "ResumenPeriodo") public ResumenPeriodo resumenPeriodo;
        @XmlElement(name = "Detalle") public List<Detalle> detalle;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Caratula {
        @XmlElement(name = "RutEmisorLibro") public String rutEmisorLibro;
        @XmlElement(name = "PeriodoTributario") public String periodoTributario; // YYYY-MM
        @XmlElement(name = "TipoOperacion") public String tipoOperacion;         // VENTA | COMPRA
        @XmlElement(name = "TipoLibro") public String tipoLibro = "MENSUAL";
        @XmlElement(name = "TipoEnvio") public String tipoEnvio = "TOTAL";
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ResumenPeriodo {
        @XmlElement(name = "TotalesPeriodo") public List<TotalesPeriodo> totalesPeriodo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TotalesPeriodo {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        @XmlElement(name = "TotDoc") public long totDoc;
        @XmlElement(name = "TotAnulado") public Long totAnulado;
        @XmlElement(name = "TotMntExe") public long totMntExe;
        @XmlElement(name = "TotMntNeto") public long totMntNeto;
        @XmlElement(name = "TotMntIVA") public long totMntIva;
        @XmlElement(name = "TotOtrosImp") public Long totOtrosImp;
        @XmlElement(name = "TotIVARet") public Long totIvaRet;
        @XmlElement(name = "TotMntTotal") public long totMntTotal;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Detalle {
        @XmlElement(name = "TpoDoc") public int tpoDoc;
        @XmlElement(name = "NroDoc") public long nroDoc;
        @XmlElement(name = "Anulado") public String anulado; // "A" si esta anulado
        @XmlElement(name = "FchDoc") public String fchDoc;   // yyyy-MM-dd
        @XmlElement(name = "RUTDoc") public String rutDoc;
        @XmlElement(name = "RznSoc") public String rznSoc;
        @XmlElement(name = "MntExe") public Long mntExe;
        @XmlElement(name = "MntNeto") public long mntNeto;
        @XmlElement(name = "MntIVA") public long mntIva;
        @XmlElement(name = "OtrosImp") public Long otrosImp;
        @XmlElement(name = "IVARet") public Long ivaRet;
        @XmlElement(name = "MntTotal") public long mntTotal;
    }
}
