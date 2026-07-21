package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import cl.nexosoftware.factura.documento.TipoDte;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitario del validador contra los XSD OFICIALES (sin contexto de
 * Spring). El corpus valido se construye con el flujo real de emision (TED con
 * CAF sintetico + firma stub schema-valida) y los casos invalidos son
 * mutaciones puntuales de ese corpus: asi el test detecta tanto regresiones del
 * validador como divergencias del generador respecto del esquema.
 */
class DteXmlValidatorTest {

    private final DteXmlValidator validator = new DteXmlValidator(true);

    private static String factura;
    private static String notaConReferencia;
    private static String boleta;
    private static String boletaExenta;

    @BeforeAll
    static void corpus() {
        factura = DteFixtures.xmlFirmado(DteFixtures.factura(1.0, 10000L, true));

        // Documento con bloque Referencia (el que llevan las notas 56/61).
        var docNota = DteFixtures.factura(1.0, 10000L, true);
        docNota.agregarReferencia(cl.nexosoftware.factura.documento.Referencia.builder()
                .tipoDocumentoRef(33)
                .folioRef(1L)
                .fechaRef(java.time.LocalDate.of(2026, 6, 26))
                .tipoReferencia(cl.nexosoftware.factura.documento.TipoReferencia.ANULA_DOCUMENTO)
                .razon("Anula la factura")
                .build());
        notaConReferencia = DteFixtures.xmlFirmado(docNota);

        boleta = DteFixtures.xmlFirmado(DteFixtures.boletaAfecta(11900L));
        boletaExenta = DteFixtures.xmlFirmado(DteFixtures.boletaExenta(8000L));
    }

    @Test
    @DisplayName("los XSD oficiales (factura y boleta) compilan al construir el validador")
    void compilaLosXsdEnConstructor() {
        assertThatCode(() -> new DteXmlValidator(true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una factura firmada bien formada pasa contra DTE_v10")
    void facturaValidaPasa() {
        assertThatCode(() -> validator.validar(factura, TipoDte.FACTURA_AFECTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un documento con bloque Referencia pasa")
    void referenciaValidaPasa() {
        assertThatCode(() -> validator.validar(notaConReferencia, TipoDte.FACTURA_AFECTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una boleta afecta pasa contra el esquema de boleta")
    void boletaValidaPasa() {
        assertThatCode(() -> validator.validar(boleta, TipoDte.BOLETA_AFECTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una boleta exenta pasa contra el esquema de boleta")
    void boletaExentaPasa() {
        assertThatCode(() -> validator.validar(boletaExenta, TipoDte.BOLETA_EXENTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una boleta NO valida contra el esquema de factura (esquemas distintos)")
    void boletaContraEsquemaDeFacturaFalla() {
        // RznSocEmisor/IndServicio no existen en DTE_v10: el cruce de esquemas
        // debe fallar — protege el ruteo por tipo del validador.
        assertThatThrownBy(() -> validator.validar(boleta, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("sin firma Signature -> DteInvalidoException (el XSD oficial la exige)")
    void sinFirmaLanza() {
        String sinFirma = factura.replaceAll("<Signature.*</Signature>", "");
        assertThatThrownBy(() -> validator.validar(sinFirma, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("sin Acteco -> DteInvalidoException (obligatorio en el XSD oficial)")
    void sinActecoLanza() {
        String malo = factura.replace("<Acteco>620200</Acteco>", "");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("falta MntTotal -> DteInvalidoException")
    void faltaMntTotalLanza() {
        String malo = factura.replaceFirst("<MntTotal>\\d+</MntTotal>", "");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("Folio no numerico -> DteInvalidoException")
    void folioNoNumericoLanza() {
        String malo = factura.replace("<Folio>1</Folio>", "<Folio>abc</Folio>");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("RUT del emisor mal formado -> DteInvalidoException (patron RUTType)")
    void rutEmisorMalFormadoLanza() {
        String malo = factura.replace("<RUTEmisor>76543210-9</RUTEmisor>",
                "<RUTEmisor>ABCDEFGH</RUTEmisor>");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("elementos de IdDoc en orden invertido -> DteInvalidoException")
    void ordenElementosInvertidoLanza() {
        String malo = factura
                .replace("<TipoDTE>33</TipoDTE>", "")
                .replace("<Folio>1</Folio>", "<Folio>1</Folio><TipoDTE>33</TipoDTE>");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("multiples errores se acumulan en getErrores()")
    void multiplesErroresSeAcumulan() {
        String malo = factura
                .replace("<RUTEmisor>76543210-9</RUTEmisor>", "<RUTEmisor>ABCDEFGH</RUTEmisor>")
                .replaceFirst("<MntTotal>\\d+</MntTotal>", "");
        assertThatThrownBy(() -> validator.validar(malo, TipoDte.FACTURA_AFECTA))
                .isInstanceOf(DteInvalidoException.class)
                .satisfies(ex -> assertThat(((DteInvalidoException) ex).getErrores())
                        .hasSizeGreaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("deshabilitado (app.dte.validar-xsd=false) no valida nada")
    void deshabilitadoNoValida() {
        DteXmlValidator apagado = new DteXmlValidator(false);
        assertThatCode(() -> apagado.validar("<no-es-un-dte/>", TipoDte.FACTURA_AFECTA))
                .doesNotThrowAnyException();
    }
}
