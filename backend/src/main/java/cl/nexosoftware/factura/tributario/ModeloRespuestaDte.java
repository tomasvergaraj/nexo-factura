package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Modelo JAXB del acuse {@code RespuestaDTE} (esquema OFICIAL
 * {@code RespuestaEnvioDTE_v10.xsd}, namespace SiiDte via package-info). Un solo
 * modelo cubre los DOS artefactos que el portal pide con este esquema, porque el
 * XSD define {@code Resultado} con un {@code <choice>}:
 * <ul>
 *   <li><b>Respuesta de Intercambio</b> — {@code Resultado/RecepcionEnvio}: acuse
 *       de recibo del sobre, con un {@code RecepcionDTE} por documento;</li>
 *   <li><b>Resultado Aprobacion Comercial</b> — {@code Resultado/ResultadoDTE}:
 *       aceptacion/rechazo comercial por documento.</li>
 * </ul>
 * Solo una de las dos listas se llena; JAXB marshalla la presente en el orden de
 * declaracion. El atributo ID del {@code Resultado} es la referencia de la firma
 * XMLDSig enveloped (se agrega despues de marshallar, como en el libro IECV).
 */
public final class ModeloRespuestaDte {

    private ModeloRespuestaDte() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "RespuestaDTE")
    public static class RespuestaDte {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "Resultado") public Resultado resultado;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Resultado {
        @XmlAttribute(name = "ID") public String id;
        @XmlElement(name = "Caratula") public Caratula caratula;
        // <choice>: se llena SOLO una. RecepcionEnvio antes que ResultadoDTE.
        @XmlElement(name = "RecepcionEnvio") public List<RecepcionEnvio> recepcionEnvio;
        @XmlElement(name = "ResultadoDTE") public List<ResultadoDte> resultadoDte;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Caratula {
        @XmlAttribute(name = "version") public String version = "1.0";
        @XmlElement(name = "RutResponde") public String rutResponde;   // RUT receptor de los DTE (nosotros)
        @XmlElement(name = "RutRecibe") public String rutRecibe;       // RUT emisor de los DTE
        @XmlElement(name = "IdRespuesta") public long idRespuesta;
        @XmlElement(name = "NroDetalles") public int nroDetalles;
        @XmlElement(name = "NmbContacto") public String nmbContacto;
        @XmlElement(name = "FonoContacto") public String fonoContacto;
        @XmlElement(name = "MailContacto") public String mailContacto;
        @XmlElement(name = "TmstFirmaResp") public String tmstFirmaResp;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RecepcionEnvio {
        @XmlElement(name = "NmbEnvio") public String nmbEnvio;
        @XmlElement(name = "FchRecep") public String fchRecep;         // dateTime
        @XmlElement(name = "CodEnvio") public long codEnvio;
        @XmlElement(name = "EnvioDTEID") public String envioDteId;
        @XmlElement(name = "Digest") public String digest;            // base64Binary, opcional
        @XmlElement(name = "RutEmisor") public String rutEmisor;
        @XmlElement(name = "RutReceptor") public String rutReceptor;
        @XmlElement(name = "EstadoRecepEnv") public int estadoRecepEnv;
        @XmlElement(name = "RecepEnvGlosa") public String recepEnvGlosa;
        @XmlElement(name = "NroDTE") public Integer nroDte;
        @XmlElement(name = "RecepcionDTE") public List<RecepcionDte> recepcionDte;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RecepcionDte {
        @XmlElement(name = "TipoDTE") public int tipoDte;
        @XmlElement(name = "Folio") public long folio;
        @XmlElement(name = "FchEmis") public String fchEmis;          // date AAAA-MM-DD
        @XmlElement(name = "RUTEmisor") public String rutEmisor;
        @XmlElement(name = "RUTRecep") public String rutRecep;
        @XmlElement(name = "MntTotal") public long mntTotal;
        @XmlElement(name = "EstadoRecepDTE") public int estadoRecepDte;
        @XmlElement(name = "RecepDTEGlosa") public String recepDteGlosa;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ResultadoDte {
        @XmlElement(name = "TipoDTE") public int tipoDte;
        @XmlElement(name = "Folio") public long folio;
        @XmlElement(name = "FchEmis") public String fchEmis;          // date AAAA-MM-DD
        @XmlElement(name = "RUTEmisor") public String rutEmisor;
        @XmlElement(name = "RUTRecep") public String rutRecep;
        @XmlElement(name = "MntTotal") public long mntTotal;
        @XmlElement(name = "CodEnvio") public long codEnvio;
        @XmlElement(name = "EstadoDTE") public int estadoDte;
        @XmlElement(name = "EstadoDTEGlosa") public String estadoDteGlosa;
        @XmlElement(name = "CodRchDsc") public Integer codRchDsc;     // negativeInteger, opcional
    }
}
