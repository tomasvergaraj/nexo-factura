package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.tributario.EnvioRecibosGenerator.ReciboItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El EnvioRecibos (Recibo de Mercaderias, Ley 19.983) firmado cumple el esquema
 * EnvioRecibos_v10 y lleva DOBLE firma (cada Recibo + el SetRecibos), con la
 * Declaracion de texto fijo que exige el XSD.
 */
class EnvioRecibosGeneratorTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-23T14:30:00Z"), ZoneId.of("America/Santiago"));

    private final DteXmlValidator validator = new DteXmlValidator(true);
    private final EnvioRecibosGenerator gen =
            new EnvioRecibosGenerator(new FirmaElectronicaStub(), validator, RELOJ);

    @Test
    @DisplayName("un Recibo del 52235 firmado cumple el XSD, con doble firma y Declaracion Ley 19.983")
    void reciboCumpleXsdConDobleFirma() {
        ReciboItem recibo = new ReciboItem(33, 52235L, LocalDate.of(2026, 7, 23),
                "88888888-8", "78397017-1", 5390L, "Casa Matriz", "11111111-1");

        String xml = gen.generar("78397017-1", "88888888-8",
                new Contacto("NEXO SOFTWARE SPA", "+56222222222", "contacto@nexosoftware.cl"),
                List.of(recibo), 1L); // valida contra el XSD adentro

        assertThat(xml)
                .contains("<SetRecibos ID=\"SetRecibos\">")
                .contains("<RutResponde>78397017-1</RutResponde>")
                .contains("<RutRecibe>88888888-8</RutRecibe>")
                .contains("<DocumentoRecibo ID=\"Recibo52235\">")
                .contains("<TipoDoc>33</TipoDoc>")
                .contains("<Folio>52235</Folio>")
                .contains("<Recinto>Casa Matriz</Recinto>")
                .contains("<RutFirma>11111111-1</RutFirma>")
                .contains(ModeloEnvioRecibos.DECLARACION_LEY_19983);
        // Doble firma: la del Recibo (sobre DocumentoRecibo) + la del SetRecibos.
        assertThat(xml.split("<Signature ", -1).length - 1).isEqualTo(2);
    }

    @Test
    @DisplayName("dos recibos generan dos DocumentoRecibo con ID distinto y tres firmas")
    void dosRecibosDosIds() {
        List<ReciboItem> recibos = List.of(
                new ReciboItem(33, 52235L, LocalDate.of(2026, 7, 23), "88888888-8", "78397017-1", 5390L, "Casa Matriz", "11111111-1"),
                new ReciboItem(33, 52240L, LocalDate.of(2026, 7, 23), "88888888-8", "78397017-1", 8000L, "Casa Matriz", "11111111-1"));

        String xml = gen.generar("78397017-1", "88888888-8", Contacto.VACIO, recibos, 1L);

        assertThat(xml)
                .contains("<DocumentoRecibo ID=\"Recibo52235\">")
                .contains("<DocumentoRecibo ID=\"Recibo52240\">");
        // Dos recibos (una firma c/u) + la del SetRecibos.
        assertThat(xml.split("<Signature ", -1).length - 1).isEqualTo(3);
    }
}
