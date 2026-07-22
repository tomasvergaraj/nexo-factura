package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * Cliente SOAP minimo para los servicios .jws del canal clasico de DTE
 * (Apache Axis 1.x en maullin/palena): envelopes 1.1 rpc/encoded armados a
 * mano, POST text/xml con {@code SOAPAction: ""}. Se evita adrede un stack
 * SOAP/WSDL: los WSDL de certificacion del SII referencian hosts internos
 * inaccesibles (nogal.sii.cl) y el envelope manual es lo robusto.
 *
 * Los .jws devuelven el resultado como un STRING XML-escapado dentro de
 * {@code <operacionReturn>}; el parser DOM lo des-escapa solo (getTextContent).
 * Las respuestas declaran charset=utf-8 pero vienen en ISO-8859-1: se leen los
 * bytes y se decodifican explicitamente. Un 500 con soapenv:Fault o un "Error
 * 500" plano del SII se traducen a {@link SiiNoDisponibleException}.
 */
@Component
@Profile("prod")
public class SiiSoap {

    private final RestClient http;

    public SiiSoap(SiiHttp siiHttp) {
        this.http = siiHttp.cliente();
    }

    /**
     * Invoca una operacion rpc/encoded y devuelve el contenido (ya des-escapado)
     * del elemento {@code <operacionReturn>}.
     *
     * @param url        URL del .jws (p.ej. https://maullin.sii.cl/DTEWS/CrSeed.jws)
     * @param operacion  nombre de la operacion (getSeed, getToken, getEstUp)
     * @param parametros pares [nombre, valor] en el ORDEN del servicio (rpc/encoded
     *                   resuelve por posicion); los valores se escapan aqui
     */
    public String invocar(String url, String operacion, String[]... parametros) {
        StringBuilder cuerpo = new StringBuilder();
        for (String[] p : parametros) {
            cuerpo.append('<').append(p[0]).append(" xsi:type=\"xsd:string\">")
                    .append(escapar(p[1]))
                    .append("</").append(p[0]).append('>');
        }
        String envelope = "<SOAP-ENV:Envelope"
                + " xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""
                + " SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<SOAP-ENV:Body>"
                + "<m:" + operacion + " xmlns:m=\"" + url + "\">" + cuerpo + "</m:" + operacion + ">"
                + "</SOAP-ENV:Body></SOAP-ENV:Envelope>";

        byte[] respuesta;
        try {
            respuesta = http.post()
                    .uri(url)
                    .contentType(new MediaType("text", "xml", StandardCharsets.UTF_8))
                    .header("SOAPAction", "\"\"")
                    .body(envelope)
                    .retrieve()
                    .body(byte[].class);
        } catch (ResourceAccessException e) {
            throw new SiiNoDisponibleException(
                    "SII no disponible (" + operacion + "): " + e.getMessage());
        } catch (RestClientResponseException e) {
            // Axis responde los fallos como 500 con soapenv:Fault.
            String fault = extraerFaultString(e.getResponseBodyAsByteArray());
            throw new SiiNoDisponibleException(
                    "SII respondio " + e.getStatusCode().value() + " en " + operacion
                            + (fault != null ? " (fault: " + fault + ")" : ""));
        } catch (RestClientException e) {
            // Conexion cortada LEYENDO la respuesta (no viene como
            // ResourceAccessException): transporte -> contingencia.
            throw new SiiNoDisponibleException(
                    "SII interrumpio la respuesta en " + operacion + ": " + e.getMessage());
        }

        String retorno = textoElemento(respuesta, operacion + "Return");
        if (retorno == null) {
            String fault = extraerFaultString(respuesta);
            throw new SiiNoDisponibleException("Respuesta SOAP inesperada del SII en " + operacion
                    + (fault != null ? " (fault: " + fault + ")" : ""));
        }
        return retorno;
    }

    /** Primer elemento con ese localName; el DOM ya des-escapa el contenido. */
    public String textoElemento(byte[] xml, String localName) {
        // Se decodifica ISO-8859-1 antes de parsear: el encoding declarado miente.
        return SiiXml.textoElemento(new String(xml, StandardCharsets.ISO_8859_1), localName);
    }

    public String textoElemento(String xml, String localName) {
        return SiiXml.textoElemento(xml, localName);
    }

    private String extraerFaultString(byte[] cuerpo) {
        if (cuerpo == null) return null;
        String texto = new String(cuerpo, StandardCharsets.ISO_8859_1);
        String fault = textoElemento(texto, "faultstring");
        if (fault != null) return fault;
        // El CGI a veces responde texto plano "Error 500".
        String plano = texto.replaceAll("\\s+", " ").trim();
        return plano.length() <= 120 ? plano : null;
    }

    private String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
