package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

/**
 * Modelo JAXB del {@code EnvioRecibos} (esquema OFICIAL {@code
 * EnvioRecibos_v10.xsd} + {@code Recibos_v10.xsd}, namespace SiiDte via
 * package-info) para el Recibo de Mercaderias (Ley 19.983).
 *
 * El sobre tiene DOBLE firma (como EnvioDTE): cada {@code Recibo} se firma sobre
 * su {@code DocumentoRecibo/@ID} y el {@code SetRecibos} se firma sobre su
 * {@code @ID}. Por eso el generador marshalla y firma cada {@code Recibo} como
 * fragmento aparte y luego los embebe verbatim; aqui solo se modelan las piezas
 * (la {@code Caratula} y el {@code Recibo}), no el sobre completo. La firma la
 * agrega {@link FirmaElectronica} despues de marshallar.
 */
public final class ModeloEnvioRecibos {

    private ModeloEnvioRecibos() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "Caratula")
    public static class Caratula {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "RutResponde") public String rutResponde;   // RUT receptor del documento (nosotros)
        @XmlElement(name = "RutRecibe") public String rutRecibe;       // RUT emisor de los documentos
        @XmlElement(name = "NmbContacto") public String nmbContacto;
        @XmlElement(name = "FonoContacto") public String fonoContacto;
        @XmlElement(name = "MailContacto") public String mailContacto;
        @XmlElement(name = "TmstFirmaEnv") public String tmstFirmaEnv;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "Recibo")
    public static class Recibo {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "DocumentoRecibo") public DocumentoRecibo documentoRecibo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DocumentoRecibo {
        @XmlAttribute(name = "ID") public String id;
        @XmlElement(name = "TipoDoc") public int tipoDoc;
        @XmlElement(name = "Folio") public long folio;
        @XmlElement(name = "FchEmis") public String fchEmis;           // date AAAA-MM-DD
        @XmlElement(name = "RUTEmisor") public String rutEmisor;
        @XmlElement(name = "RUTRecep") public String rutRecep;
        @XmlElement(name = "MntTotal") public long mntTotal;
        @XmlElement(name = "Recinto") public String recinto;
        @XmlElement(name = "RutFirma") public String rutFirma;         // RUN de quien firma el recibo
        @XmlElement(name = "Declaracion") public String declaracion;   // texto fijo Ley 19.983
        @XmlElement(name = "TmstFirmaRecibo") public String tmstFirmaRecibo;
    }

    /** Texto FIJO que exige el XSD (Recibos_v10, elemento Declaracion). */
    public static final String DECLARACION_LEY_19983 =
            "El acuse de recibo que se declara en este acto, de acuerdo a lo dispuesto "
                    + "en la letra b) del Art. 4, y la letra c) del Art. 5 de la Ley 19.983, "
                    + "acredita que la entrega de mercaderias o servicio(s) prestado(s) "
                    + "ha(n) sido recibido(s).";
}
