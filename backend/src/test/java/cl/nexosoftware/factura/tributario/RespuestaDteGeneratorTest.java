package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.AcuseEnvio;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.Cabecera;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La RespuestaDTE (Respuesta de Intercambio y Resultado Comercial) firmada
 * cumple el esquema oficial RespuestaEnvioDTE_v10 y modela la trampa del set:
 * el DTE dirigido a otro RUT se rechaza con EstadoRecepDTE=3.
 */
class RespuestaDteGeneratorTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-23T14:30:00Z"), ZoneId.of("America/Santiago"));

    private final DteXmlValidator validator = new DteXmlValidator(true);
    private final RespuestaDteGenerator gen =
            new RespuestaDteGenerator(new FirmaElectronicaStub(), validator, RELOJ);

    // 52235 va dirigido a nosotros (78397017-1); 52236 a otro RUT (la trampa).
    private static final SobreRecibido.DteRecibido D_NUESTRO = new SobreRecibido.DteRecibido(
            33, 52235L, LocalDate.of(2026, 7, 23), "88888888-8", "78397017-1", 5390L);
    private static final SobreRecibido.DteRecibido D_AJENO = new SobreRecibido.DteRecibido(
            33, 52236L, LocalDate.of(2013, 6, 21), "88888888-8", "69507000-4", 7770L);

    private static final Cabecera CAB = new Cabecera("78397017-1", "88888888-8",
            new Contacto("NEXO SOFTWARE SPA", "+56222222222", "contacto@nexosoftware.cl"), 1753278600L);

    @Test
    @DisplayName("Respuesta de Intercambio: acepta 52235 (0) y rechaza 52236 por RUT receptor (3), cumple el XSD")
    void recepcionEnvioModelaLaTrampa() {
        List<DteEvaluado> dtes = List.of(
                new DteEvaluado(D_NUESTRO, true, 0),
                new DteEvaluado(D_AJENO, false, 3));
        AcuseEnvio acuse = new AcuseEnvio("set_intercambio.xml", LocalDateTime.now(RELOJ), 1753278600L,
                "SetDoc", "1WGHYu7oiVjSTV1/Bjcejc02gcA=", "88888888-8", "78397017-1", 0, dtes);

        String xml = gen.generarRecepcionEnvio(CAB, acuse); // valida contra el XSD adentro

        assertThat(xml)
                .contains("xsi:schemaLocation=\"http://www.sii.cl/SiiDte RespuestaEnvioDTE_v10.xsd\"")
                .contains("<Resultado ID=\"Respuesta\">")
                .contains("<RutResponde>78397017-1</RutResponde>")
                .contains("<RutRecibe>88888888-8</RutRecibe>")
                .contains("<EnvioDTEID>SetDoc</EnvioDTEID>")
                .contains("<Digest>1WGHYu7oiVjSTV1/Bjcejc02gcA=</Digest>")
                .contains("<EstadoRecepEnv>0</EstadoRecepEnv>")
                .contains("<NroDTE>2</NroDTE>")
                .contains("<Folio>52235</Folio>")
                .contains("<EstadoRecepDTE>0</EstadoRecepDTE>")
                .contains("<Folio>52236</Folio>")
                .contains("<EstadoRecepDTE>3</EstadoRecepDTE>")
                .contains("DTE No Recibido - Error en RUT Receptor");
        // Una sola firma (sobre el Resultado).
        assertThat(xml.split("<Signature ", -1).length - 1).isEqualTo(1);
    }

    @Test
    @DisplayName("Resultado Comercial: acepta el 52235 (EstadoDTE 0), no incluye el 52236, cumple el XSD")
    void resultadoComercialSoloAceptados() {
        List<DteEvaluado> aceptados = List.of(new DteEvaluado(D_NUESTRO, true, 0));

        String xml = gen.generarResultadoComercial(CAB, 1753278600L, aceptados);

        assertThat(xml)
                .contains("<ResultadoDTE>")
                .contains("<Folio>52235</Folio>")
                .contains("<EstadoDTE>0</EstadoDTE>")
                .contains("DTE Aceptado OK")
                .contains("<CodEnvio>1753278600</CodEnvio>")
                .doesNotContain("<Folio>52236</Folio>")
                .doesNotContain("<RecepcionEnvio>");
        assertThatCode(() -> validator.validarRespuestaDte(xml)).doesNotThrowAnyException();
    }
}
