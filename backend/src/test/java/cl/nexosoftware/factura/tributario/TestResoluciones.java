package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.empresa.EmpresaRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Helper de tests: un {@link ResolucionResolver} respaldado por un repositorio
 * de empresas VACIO, de modo que toda empresa cae al fallback de entorno
 * (APP_SII_FCH_RESOL/NRO_RESOL de las {@link AppProperties} dadas) — el
 * comportamiento del modo GLOBAL / ambiente de certificacion.
 */
public final class TestResoluciones {

    private TestResoluciones() {}

    public static ResolucionResolver deEntorno(AppProperties props) {
        EmpresaRepository repo = mock(EmpresaRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        return new ResolucionResolver(repo, props);
    }
}
