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
 *   BORRADOR  -> FIRMADO
 *   FIRMADO   -> ENVIADO | BORRADOR
 *   ENVIADO   -> ACEPTADO | RECHAZADO | REPARO
 *   ACEPTADO  -> ANULADO
 *   RECHAZADO -> BORRADOR
 *   REPARO    -> ACEPTADO | RECHAZADO
 *   ANULADO   -> (terminal)
 * </pre>
 */
class EstadoDteTransicionesTest {

    @Nested
    @DisplayName("Transiciones validas")
    class Validas {

        @ParameterizedTest(name = "{0} -> {1} es valida")
        @CsvSource({
                "BORRADOR,  FIRMADO",
                "FIRMADO,   ENVIADO",
                "FIRMADO,   BORRADOR",
                "ENVIADO,   ACEPTADO",
                "ENVIADO,   RECHAZADO",
                "ENVIADO,   REPARO",
                "ACEPTADO,  ANULADO",
                "RECHAZADO, BORRADOR",
                "REPARO,    ACEPTADO",
                "REPARO,    RECHAZADO"
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
                "BORRADOR,  ENVIADO",
                "BORRADOR,  ACEPTADO",
                "BORRADOR,  ANULADO",
                "FIRMADO,   ACEPTADO",
                "FIRMADO,   ANULADO",
                // retrocesos no permitidos
                "ENVIADO,   FIRMADO",
                "ENVIADO,   BORRADOR",
                "ACEPTADO,  ENVIADO",
                "ACEPTADO,  RECHAZADO",
                // estados finales que no llevan a ANULADO directo
                "RECHAZADO, ANULADO",
                "REPARO,    ANULADO"
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
}
