package cl.nexosoftware.factura.compra;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.compra.CompraDtos.CompraRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitario puro de las reglas de negocio del registro de compras:
 * tipos admitidos y coherencia aritmetica de los montos.
 */
class CompraValidacionTest {

    @ParameterizedTest(name = "tipo {0} es admitido")
    @ValueSource(ints = {30, 32, 33, 34, 45, 46, 55, 56, 60, 61})
    void aceptaLosTiposDelLibroDeCompras(int tipo) {
        assertThatCode(() -> CompraService.validar(request(tipo, 100000, 0, 19000, 119000)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "tipo {0} se rechaza")
    @ValueSource(ints = {39, 41, 0, 110})
    void rechazaTiposFueraDelCatalogo(int tipo) {
        assertThatThrownBy(() -> CompraService.validar(request(tipo, 100000, 0, 19000, 119000)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no admitido");
    }

    @Test
    @DisplayName("IVA uso comun y codigo no recuperable son excluyentes")
    void rechazaUsoComunYNoRecuperableJuntos() {
        CompraRequest req = new CompraRequest(30, 781L, "76543210-9", "Proveedor SpA",
                LocalDate.of(2026, 7, 10), 29774L, 0L, 5657L, null, 35431L, null, true, 4);
        assertThatThrownBy(() -> CompraService.validar(req))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("excluyentes");
    }

    @Test
    @DisplayName("un codigo de IVA no recuperable desconocido se rechaza")
    void rechazaCodigoNoRecuperableDesconocido() {
        CompraRequest req = new CompraRequest(33, 67L, "76543210-9", "Proveedor SpA",
                LocalDate.of(2026, 7, 10), 9962L, 0L, 1893L, null, 11855L, null, null, 7);
        assertThatThrownBy(() -> CompraService.validar(req))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("desconocido");
    }

    @Test
    @DisplayName("uso comun requiere IVA mayor que cero")
    void rechazaUsoComunSinIva() {
        CompraRequest req = new CompraRequest(30, 781L, "76543210-9", "Proveedor SpA",
                LocalDate.of(2026, 7, 10), 29774L, 0L, 0L, null, 29774L, null, true, null);
        assertThatThrownBy(() -> CompraService.validar(req))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    @DisplayName("la entrega gratuita (cod 4) con IVA coherente es valida")
    void aceptaEntregaGratuita() {
        CompraRequest req = new CompraRequest(33, 67L, "76543210-9", "Proveedor SpA",
                LocalDate.of(2026, 7, 10), 9962L, 0L, 1893L, null, 11855L, null, null, 4);
        assertThatCode(() -> CompraService.validar(req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el total debe ser neto + exento + IVA - IVA retenido")
    void rechazaTotalIncoherente() {
        assertThatThrownBy(() -> CompraService.validar(request(33, 100000, 0, 19000, 118000)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    @DisplayName("una compra exenta pura (solo exento) es coherente")
    void aceptaCompraExenta() {
        assertThatCode(() -> CompraService.validar(request(34, 0, 30000, 0, 30000)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una factura de compra (46) con retencion total es coherente: total = neto")
    void aceptaRetencionTotal() {
        assertThatCode(() -> CompraService.validar(
                request(46, 100000, 0, 19000, 19000L, 100000)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la retencion no puede exceder el IVA del documento")
    void rechazaRetencionMayorQueIva() {
        assertThatThrownBy(() -> CompraService.validar(
                request(46, 100000, 0, 19000, 20000L, 99000)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("exceder");
    }

    @Test
    @DisplayName("con retencion, el total debe descontarla")
    void rechazaTotalSinDescontarRetencion() {
        assertThatThrownBy(() -> CompraService.validar(
                request(46, 100000, 0, 19000, 19000L, 119000)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no coincide");
    }

    private static CompraRequest request(int tipo, long neto, long exento, long iva, long total) {
        return request(tipo, neto, exento, iva, null, total);
    }

    private static CompraRequest request(int tipo, long neto, long exento, long iva,
                                         Long ivaRetenido, long total) {
        return new CompraRequest(tipo, 100L, "76543210-9", "Proveedor SpA",
                LocalDate.of(2026, 7, 10), neto, exento, iva, ivaRetenido, total, null);
    }
}
