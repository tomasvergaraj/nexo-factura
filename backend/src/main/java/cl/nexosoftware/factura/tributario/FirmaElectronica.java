package cl.nexosoftware.factura.tributario;

/**
 * Firma electronica del DTE (XMLDSig).
 *
 * El SII exige que el XML del DTE vaya firmado con el certificado digital del
 * representante legal de la empresa (formato PKCS#12). La implementacion real
 * carga el certificado, calcula el digest del documento y agrega el nodo
 * {@code <Signature>} enveloped. Aqui se define la abstraccion para poder
 * inyectar una implementacion real o un stub segun el ambiente.
 */
public interface FirmaElectronica {

    /**
     * Devuelve el XML firmado.
     *
     * @param xmlDte XML del DTE sin firmar
     * @return XML con el nodo Signature incorporado
     */
    String firmar(String xmlDte);
}
