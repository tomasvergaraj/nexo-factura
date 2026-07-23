package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Sobre {@code EnvioBOLETA} (EnvioBOLETA_v11.xsd) para un DTE de boleta ya
 * firmado. Toda la mecanica (caratula, firma del SetDTE, validacion) vive en
 * {@link EnvioGenerator}.
 */
@Component
@Profile("prod")
public class EnvioBoletaGenerator extends EnvioGenerator {

    // @Autowired explicito: hay un segundo constructor (con Clock, para tests)
    // y Spring no elige solo entre dos.
    @Autowired
    public EnvioBoletaGenerator(FirmaElectronica firma, DteXmlValidator validator,
                                CertificadoResolver certificadoResolver,
                                ResolucionResolver resolucionResolver, AppProperties props) {
        this(firma, validator, certificadoResolver, resolucionResolver, props,
                Clock.system(ZoneId.of("America/Santiago")));
    }

    EnvioBoletaGenerator(FirmaElectronica firma, DteXmlValidator validator,
                         CertificadoResolver certificadoResolver,
                         ResolucionResolver resolucionResolver, AppProperties props, Clock clock) {
        super(firma, validator, certificadoResolver, resolucionResolver, props, clock);
    }

    @Override
    String nombreSobre() {
        return "EnvioBOLETA";
    }

    @Override
    String esquema() {
        return "EnvioBOLETA_v11.xsd";
    }

    @Override
    void validar(String sobreFirmado) {
        validator().validarEnvioBoleta(sobreFirmado);
    }
}
