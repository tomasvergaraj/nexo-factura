package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Sobre {@code EnvioDTE} (EnvioDTE_v10.xsd) para un DTE de factura/notas ya
 * firmado — el gemelo de {@link EnvioBoletaGenerator} para el canal clasico.
 * Toda la mecanica (caratula, firma del SetDTE, validacion) vive en
 * {@link EnvioGenerator}.
 */
@Component
@Profile("prod")
public class EnvioDteGenerator extends EnvioGenerator {

    // @Autowired explicito: hay un segundo constructor (con Clock, para tests)
    // y Spring no elige solo entre dos.
    @Autowired
    public EnvioDteGenerator(FirmaElectronica firma, DteXmlValidator validator,
                             CertificadoDigital certificado, AppProperties props) {
        this(firma, validator, certificado, props, Clock.system(ZoneId.of("America/Santiago")));
    }

    EnvioDteGenerator(FirmaElectronica firma, DteXmlValidator validator,
                      CertificadoDigital certificado, AppProperties props, Clock clock) {
        super(firma, validator, certificado, props, clock);
    }

    @Override
    String nombreSobre() {
        return "EnvioDTE";
    }

    @Override
    String esquema() {
        return "EnvioDTE_v10.xsd";
    }

    @Override
    void validar(String sobreFirmado) {
        validator().validarEnvioDte(sobreFirmado);
    }
}
