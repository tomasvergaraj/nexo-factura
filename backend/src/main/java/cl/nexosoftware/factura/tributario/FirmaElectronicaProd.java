package cl.nexosoftware.factura.tributario;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Esqueleto de {@link FirmaElectronica} para el perfil prod.
 *
 * El bean existe para que el contexto del perfil prod levante (fail-fast en el
 * arranque solo lo aplica {@code JwtSecretValidator}), pero la firma XMLDSig real
 * todavia no esta implementada: cargar el PKCS#12 configurado en
 * app.sii.certificado-path, calcular el digest del DTE y agregar el nodo
 * {@code <Signature>} enveloped con java.security + javax.xml.crypto.dsig.
 * Hasta entonces cada operacion falla de forma explicita en lugar de emitir un
 * documento invalido ante el SII.
 */
@Component
@Profile("prod")
@Slf4j
public class FirmaElectronicaProd implements FirmaElectronica {

    @PostConstruct
    void avisar() {
        log.warn("FirmaElectronicaProd activa: la firma XMLDSig real esta PENDIENTE. "
                + "Las operaciones de firma fallaran hasta integrar el certificado PKCS#12.");
    }

    @Override
    public String firmar(String xmlDte) {
        throw new UnsupportedOperationException(
                "Firma XMLDSig real pendiente: requiere certificado PKCS#12");
    }
}
