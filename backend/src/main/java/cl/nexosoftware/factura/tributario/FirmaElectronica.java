package cl.nexosoftware.factura.tributario;

/**
 * Firma electronica XMLDSig segun el perfil que exige el SII (fijado por su
 * esquema xmldsignature_v10.xsd): C14N inclusive + rsa-sha1 + digest sha1,
 * firma enveloped como ultimo hijo de la raiz, KeyInfo con KeyValue y X509Data.
 *
 * Toda firma es POR EMPRESA: {@code empresaId} decide con que certificado se
 * firma via {@link CertificadoResolver} (en modo GLOBAL toda empresa resuelve
 * al mismo PKCS#12 del ambiente; el stub de desarrollo lo ignora).
 */
public interface FirmaElectronica {

    /**
     * Firma el DTE: Reference al atributo ID del elemento Documento y nodo
     * Signature como ultimo hijo de {@code <DTE>}.
     *
     * @param xmlDte    XML del DTE sin firmar (con TED ya incorporado)
     * @param empresaId empresa emisora (decide el certificado)
     * @return XML con el nodo Signature incorporado
     */
    String firmar(String xmlDte, Long empresaId);

    /**
     * Firma enveloped generica: para el getToken del SII ({@code refId} null →
     * {@code Reference URI=""}, documento completo) y para los sobres de envio
     * ({@code refId} = valor del atributo ID del SetDTE → {@code URI="#refId"}).
     */
    String firmarEnveloped(String xml, String refId, Long empresaId);
}
