package cl.nexosoftware.factura.tributario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementacion de marcador de posicion de {@link FirmaElectronica}.
 *
 * Inserta un nodo Signature simbolico para que el flujo completo de emision sea
 * ejecutable sin un certificado real. Para produccion, reemplazar por una
 * implementacion basada en java.security + XMLDSig (javax.xml.crypto.dsig) que
 * cargue el PKCS#12 configurado en app.sii.certificado-path.
 */
@Component
@Profile("!produccion")
@Slf4j
public class FirmaElectronicaStub implements FirmaElectronica {

    @Override
    public String firmar(String xmlDte) {
        log.warn("Firmando DTE con stub: este XML NO es valido ante el SII. "
                + "Configure un certificado real para el ambiente de produccion.");
        String firmaSimbolica = """
                <Signature xmlns="http://www.w3.org/2000/09/xmldsig#" estado="STUB">
                  <SignedInfo/>
                  <SignatureValue>FIRMA-PENDIENTE-CERTIFICADO</SignatureValue>
                </Signature>
                """;
        return xmlDte.replace("</DTE>", firmaSimbolica + "</DTE>");
    }
}
