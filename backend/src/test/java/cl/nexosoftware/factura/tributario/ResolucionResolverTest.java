package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orden de resolucion de la resolucion SII: la fila de la empresa manda; si esta
 * vacia rige el fallback de entorno; una fila a medias es un error explicito.
 */
class ResolucionResolverTest {

    private static AppProperties props(String fchResolEnv, int nroResolEnv) {
        return new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", "POR_EMPRESA", null, null, null, fchResolEnv, nroResolEnv, "UA"), null);
    }

    private static EmpresaRepository repoCon(Empresa empresa) {
        EmpresaRepository repo = mock(EmpresaRepository.class);
        when(repo.findById(any())).thenReturn(Optional.ofNullable(empresa));
        return repo;
    }

    private static Empresa empresa(LocalDate fchResol, Integer nroResol) {
        return Empresa.builder().rut("76543210-9").razonSocial("X").giro("y")
                .direccion("z").comuna("c").fchResol(fchResol).nroResol(nroResol).build();
    }

    @Test
    @DisplayName("la resolucion propia de la empresa tiene prioridad sobre el entorno")
    void resolucionPropiaMandaSobreEntorno() {
        var resolver = new ResolucionResolver(
                repoCon(empresa(LocalDate.of(2024, 3, 10), 80)), props("2026-05-14", 0));

        var res = resolver.paraCaratula(5L);
        assertThat(res.fchResol()).isEqualTo("2024-03-10");
        assertThat(res.nroResol()).isEqualTo(80);
    }

    @Test
    @DisplayName("sin resolucion propia cae al fallback de entorno")
    void fallbackDeEntorno() {
        var resolver = new ResolucionResolver(repoCon(empresa(null, null)), props("2026-05-14", 0));

        var res = resolver.paraCaratula(5L);
        assertThat(res.fchResol()).isEqualTo("2026-05-14");
        assertThat(res.nroResol()).isEqualTo(0);
    }

    @Test
    @DisplayName("resolucion propia incompleta (solo fecha) es un error de negocio")
    void incompletaFalla() {
        var resolver = new ResolucionResolver(
                repoCon(empresa(LocalDate.of(2024, 3, 10), null)), props("2026-05-14", 0));

        assertThatThrownBy(() -> resolver.paraCaratula(5L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("incompleta");
    }

    @Test
    @DisplayName("sin resolucion propia ni fallback: paraCaratula falla, siExiste vacio")
    void sinNadaFalla() {
        var resolver = new ResolucionResolver(repoCon(empresa(null, null)), props("", 0));

        assertThat(resolver.siExiste(5L)).isEmpty();
        assertThatThrownBy(() -> resolver.paraCaratula(5L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no tiene resolucion SII");
    }
}
