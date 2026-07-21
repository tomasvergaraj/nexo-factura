package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Sobre EnvioDTE del canal clasico: misma caratula que el de boleta, DTE de
 * factura embebido verbatim y validacion del sobre completo contra
 * EnvioDTE_v10.xsd.
 */
class EnvioDteGeneratorTest {

    private static final Clock RELOJ_FIJO =
            Clock.fixed(Instant.parse("2026-06-26T18:03:21Z"), ZoneId.of("America/Santiago"));

    private static EnvioDteGenerator generador;

    @BeforeAll
    static void inicializar() throws Exception {
        String path = new ClassPathResource("sii/cert_prueba.p12").getFile().getAbsolutePath();
        AppProperties props = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", path, "test123", null, "2026-05-14", 0, "Mozilla/4.0 (compatible; PROG 1.0)"));
        CertificadoDigital certificado = new CertificadoDigital(props);
        generador = new EnvioDteGenerator(new FirmaElectronicaProd(certificado),
                new DteXmlValidator(true), certificado, props, RELOJ_FIJO);
    }

    @Test
    @DisplayName("el sobre EnvioDTE firmado valida contra EnvioDTE_v10.xsd")
    void sobreValidaContraElEsquema() {
        String dte = DteFixtures.xmlFirmado(DteFixtures.factura(1.0, 10000L, true));
        SiiGateway.EnvioSii envio = new SiiGateway.EnvioSii(dte, 33, 1L, DteFixtures.RUT_EMISOR);

        assertThatCode(() -> generador.generar(envio)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("caratula fiel y DTE embebido verbatim con dos firmas")
    void caratulaYDteVerbatim() {
        String dte = DteFixtures.xmlFirmado(DteFixtures.factura(1.0, 10000L, true));
        SiiGateway.EnvioSii envio = new SiiGateway.EnvioSii(dte, 33, 1L, DteFixtures.RUT_EMISOR);
        String sobre = generador.generar(envio);

        assertThat(sobre)
                .contains("<EnvioDTE xmlns=\"http://www.sii.cl/SiiDte\"")
                .contains("<RutEmisor>76543210-9</RutEmisor>")
                .contains("<RutEnvia>11111111-1</RutEnvia>")
                .contains("<RutReceptor>60803000-K</RutReceptor>")
                .contains("<FchResol>2026-05-14</FchResol>")
                .contains("<NroResol>0</NroResol>")
                .contains("<SubTotDTE><TpoDTE>33</TpoDTE><NroDTE>1</NroDTE></SubTotDTE>");
        // El xmlns redundante del <DTE> interno puede omitirse al re-serializar
        // (mismo default que el sobre); el contenido del Documento es verbatim.
        String contenido = dte.substring(dte.indexOf("<Documento"), dte.indexOf("</DTE>"));
        assertThat(sobre).contains(contenido);
        // "<Signature " con espacio: no contar SignatureValue/SignatureMethod.
        assertThat(sobre.split("<Signature ", -1).length - 1).isEqualTo(2);
    }
}
