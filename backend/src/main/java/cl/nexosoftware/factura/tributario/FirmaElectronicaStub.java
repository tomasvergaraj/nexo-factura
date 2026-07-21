package cl.nexosoftware.factura.tributario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementacion de marcador de posicion de {@link FirmaElectronica} (perfiles
 * de desarrollo, sin certificado).
 *
 * Inserta una firma FALSA pero con la FORMA que exige el esquema
 * xmldsignature_v10.xsd del SII (estructura y algoritmos correctos, valores
 * basura en base64): asi el flujo completo de emision — incluida la validacion
 * contra los XSD OFICIALES, que exigen el nodo Signature — es ejecutable sin un
 * certificado real. Este XML por supuesto NO es valido ante el SII.
 */
@Component
@Profile("!prod")
@Slf4j
public class FirmaElectronicaStub implements FirmaElectronica {

    // Estructura completa del Signature restringido del SII; "U1RVQg==" es
    // base64 de "STUB" (los tipos base64Binary del esquema no validan contenido).
    private static final String FIRMA_FALSA =
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">"
                    + "<SignedInfo>"
                    + "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>"
                    + "<SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>"
                    + "<Reference URI=\"%s\">"
                    + "<Transforms>"
                    + "<Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>"
                    + "</Transforms>"
                    + "<DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\"/>"
                    + "<DigestValue>U1RVQg==</DigestValue>"
                    + "</Reference>"
                    + "</SignedInfo>"
                    + "<SignatureValue>U1RVQkZJUk1BUEVORElFTlRFQ0VSVElGSUNBRE8=</SignatureValue>"
                    + "<KeyInfo>"
                    + "<KeyValue><RSAKeyValue><Modulus>U1RVQg==</Modulus><Exponent>Aw==</Exponent></RSAKeyValue></KeyValue>"
                    + "<X509Data><X509Certificate>U1RVQg==</X509Certificate></X509Data>"
                    + "</KeyInfo>"
                    + "</Signature>";

    @Override
    public String firmar(String xmlDte) {
        log.warn("Firmando DTE con stub: este XML NO es valido ante el SII. "
                + "Configure un certificado real para el perfil prod.");
        return insertarAntesDelCierre(xmlDte, referencia(xmlDte));
    }

    @Override
    public String firmarEnveloped(String xml, String refId) {
        log.warn("Firmando sobre/getToken con stub: NO es valido ante el SII.");
        return insertarAntesDelCierre(xml, refId == null ? "" : "#" + refId);
    }

    /** Extrae el ID del Documento para que la Reference del stub sea realista. */
    private String referencia(String xmlDte) {
        int i = xmlDte.indexOf("ID=\"");
        if (i < 0) return "#STUB";
        int fin = xmlDte.indexOf('"', i + 4);
        return fin > i ? "#" + xmlDte.substring(i + 4, fin) : "#STUB";
    }

    private String insertarAntesDelCierre(String xml, String uri) {
        int cierre = xml.lastIndexOf("</");
        if (cierre < 0) {
            throw new IllegalStateException("XML sin tag de cierre: no se puede insertar la firma stub");
        }
        return xml.substring(0, cierre) + FIRMA_FALSA.formatted(uri) + xml.substring(cierre);
    }
}
