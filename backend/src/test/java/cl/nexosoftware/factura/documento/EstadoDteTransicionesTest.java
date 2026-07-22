package cl.nexosoftware.factura.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro (sin Spring) de la maquina de estados {@link EstadoDte}.
 *
 * Verifica el grafo de transiciones declarado en el enum:
 * <pre>
 *   BORRADOR        -> FIRMADO
 *   FIRMADO         -> ENVIADO | BORRADOR | EN_CONTINGENCIA
 *   EN_CONTINGENCIA -> ENVIADO | RECHAZADO | ACEPTADO | REPARO
 *                      (ACEPTADO/REPARO solo via reconciliacion por folio: el
 *                       SII ya tenia el documento aunque la respuesta se perdio)
 *   ENVIADO         -> ACEPTADO | RECHAZADO | REPARO
 *   ACEPTADO        -> ANULADO
 *   RECHAZADO       -> ENVIADO   (reenvio; nunca a EN_CONTINGENCIA: un rechazo
 *                                 es de fondo, no una caida transitoria)
 *   REPARO          -> ACEPTADO | RECHAZADO
 *   ANULADO         -> (terminal)
 * </pre>
 */
class EstadoDteTransicionesTest {

    @Nested
    @DisplayName("Transiciones validas")
    class Validas {

        @ParameterizedTest(name = "{0} -> {1} es valida")
        @CsvSource({
                "BORRADOR,        FIRMADO",
                "FIRMADO,         ENVIADO",
                "FIRMADO,         BORRADOR",
                "FIRMADO,         EN_CONTINGENCIA",
                "EN_CONTINGENCIA, ENVIADO",
                // el SII respondio con un rechazo de negocio durante el reintento:
                // el documento sale de la cola de contingencia con el motivo
                "EN_CONTINGENCIA, RECHAZADO",
                // reconciliacion por folio: el primer envio SI llego al SII
                // (la respuesta se perdio) y ya fue procesado
                "EN_CONTINGENCIA, ACEPTADO",
                "EN_CONTINGENCIA, REPARO",
                "ENVIADO,         ACEPTADO",
                "ENVIADO,         RECHAZADO",
                "ENVIADO,         REPARO",
                "ACEPTADO,        ANULADO",
                "RECHAZADO,       ENVIADO",
                "REPARO,          ACEPTADO",
                "REPARO,          RECHAZADO"
        })
        void permiteLasTransicionesDelCicloDeVida(EstadoDte origen, EstadoDte destino) {
            assertThat(origen.puedeTransicionarA(destino))
                    .as("%s -> %s deberia ser valida", origen, destino)
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Transiciones invalidas")
    class Invalidas {

        @ParameterizedTest(name = "{0} -> {1} es invalida")
        @CsvSource({
                // saltos de etapa
                "BORRADOR,        ENVIADO",
                "BORRADOR,        ACEPTADO",
                "BORRADOR,        ANULADO",
                "BORRADOR,        EN_CONTINGENCIA",
                "FIRMADO,         ACEPTADO",
                "FIRMADO,         ANULADO",
                // retrocesos no permitidos
                "ENVIADO,         FIRMADO",
                "ENVIADO,         BORRADOR",
                "ENVIADO,         EN_CONTINGENCIA",
                "ACEPTADO,        ENVIADO",
                "ACEPTADO,        RECHAZADO",
                "EN_CONTINGENCIA, FIRMADO",
                "EN_CONTINGENCIA, BORRADOR",
                // el DTE es inmutable y su folio ya fue consumido: un rechazado
                // NO vuelve a borrador, solo puede reenviarse
                "RECHAZADO,       BORRADOR",
                // un rechazo del SII es de fondo: el documento no entra a la
                // cola de contingencia aunque el reenvio falle
                "RECHAZADO,       EN_CONTINGENCIA",
                // estados que no llevan a ANULADO directo
                "RECHAZADO,       ANULADO",
                "REPARO,          ANULADO",
                "EN_CONTINGENCIA, ANULADO"
        })
        void rechazaLasTransicionesFueraDelGrafo(EstadoDte origen, EstadoDte destino) {
            assertThat(origen.puedeTransicionarA(destino))
                    .as("%s -> %s deberia ser invalida", origen, destino)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("ANULADO es terminal")
    class Terminal {

        @ParameterizedTest(name = "ANULADO -> {0} es invalida")
        @EnumSource(EstadoDte.class)
        void noSaleDeAnulado(EstadoDte destino) {
            assertThat(EstadoDte.ANULADO.puedeTransicionarA(destino))
                    .as("ANULADO no puede transicionar a ningun estado (incluido %s)", destino)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("No hay auto-transiciones")
    class NoAutoTransicion {

        @ParameterizedTest(name = "{0} -> {0} es invalida")
        @EnumSource(EstadoDte.class)
        void ningunEstadoTransicionaASiMismo(EstadoDte estado) {
            assertThat(estado.puedeTransicionarA(estado))
                    .as("%s no deberia poder transicionar a si mismo", estado)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("El destino ANULADO solo es alcanzable desde ACEPTADO")
    void anuladoSoloDesdeAceptado() {
        for (EstadoDte origen : EstadoDte.values()) {
            boolean esperado = origen == EstadoDte.ACEPTADO;
            assertThat(origen.puedeTransicionarA(EstadoDte.ANULADO))
                    .as("%s -> ANULADO", origen)
                    .isEqualTo(esperado);
        }
    }

    @Test
    @DisplayName("El destino ENVIADO es alcanzable desde FIRMADO, EN_CONTINGENCIA y RECHAZADO")
    void enviadoDesdeFirmadoContingenciaYRechazado() {
        for (EstadoDte origen : EstadoDte.values()) {
            boolean esperado = origen == EstadoDte.FIRMADO
                    || origen == EstadoDte.EN_CONTINGENCIA
                    || origen == EstadoDte.RECHAZADO;
            assertThat(origen.puedeTransicionarA(EstadoDte.ENVIADO))
                    .as("%s -> ENVIADO", origen)
                    .isEqualTo(esperado);
        }
    }
}
