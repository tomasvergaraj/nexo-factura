package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.config.RevisionLibroProperties;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.libro.LibroDtos.EnvioLibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.SiiGateway.EstadoEnvio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consulta automatica del estado de los envios de libro. El job recorre los
 * envios sin resolucion y pide cada uno al SII; lo que se prueba aqui es la
 * resiliencia del lote —que un TrackID roto no se lleve puestos los demas— y el
 * respeto del interruptor, que es donde un fallo pasaria inadvertido.
 */
class RevisionLibroJobTest {

    private static final Long EMPRESA = 7L;

    private EmpresaRepository empresaRepository;
    private CertificadoResolver certificadoResolver;
    private RevisionLibroService revisionService;
    private LibroEnvioService envioService;

    @BeforeEach
    void setUp() {
        empresaRepository = mock(EmpresaRepository.class);
        certificadoResolver = mock(CertificadoResolver.class);
        revisionService = mock(RevisionLibroService.class);
        envioService = mock(LibroEnvioService.class);
        when(empresaRepository.findAll()).thenReturn(List.of(empresa()));
    }

    @Test
    @DisplayName("consulta el estado de cada envio pendiente y no toca los ya resueltos")
    void consultaLosPendientes() {
        when(envioService.pendientesDeResolucion(EMPRESA))
                .thenReturn(List.of(envio("T1", "2026-06"), envio("T2", "2026-07")));
        when(envioService.estadoEnvio(eq(EMPRESA), any())).thenReturn(EstadoEnvio.ACEPTADO);

        job(true).consultarEstadosDeEnvio();

        // La lista de trabajo ya excluye los terminales: el job pide exactamente
        // los que le entregan, ni uno mas.
        verify(envioService).estadoEnvio(EMPRESA, "T1");
        verify(envioService).estadoEnvio(EMPRESA, "T2");
    }

    @Test
    @DisplayName("un TrackID que falla no aborta el resto del lote")
    void unFalloNoAbortaElLote() {
        when(envioService.pendientesDeResolucion(EMPRESA))
                .thenReturn(List.of(envio("ROTO", "2026-06"), envio("T2", "2026-07")));
        when(envioService.estadoEnvio(EMPRESA, "ROTO"))
                .thenThrow(new IllegalStateException("el SII no reconoce el TrackID"));
        when(envioService.estadoEnvio(EMPRESA, "T2")).thenReturn(EstadoEnvio.RECHAZADO);

        job(true).consultarEstadosDeEnvio();

        // El segundo se consulta igual: si el primero abortara, un libro RECHAZADO
        // podria quedar sin detectar por culpa de otro envio no relacionado.
        verify(envioService).estadoEnvio(EMPRESA, "T2");
    }

    @Test
    @DisplayName("con la revision deshabilitada no consulta nada")
    void respetaElInterruptor() {
        job(false).consultarEstadosDeEnvio();

        verify(envioService, never()).pendientesDeResolucion(anyLong());
        verify(envioService, never()).estadoEnvio(anyLong(), any());
    }

    // ---------- helpers ----------

    private RevisionLibroJob job(boolean enabled) {
        // Modo GLOBAL: todas las empresas pueden firmar sin certificado propio, asi
        // el test se concentra en el recorrido y no en la resolucion del certificado.
        AppProperties app = new AppProperties(null, null,
                new AppProperties.Sii("CERT", "GLOBAL", null, null, null, null, 0, null), null);
        return new RevisionLibroJob(new RevisionLibroProperties(enabled, 5), app,
                empresaRepository, certificadoResolver, revisionService, envioService,
                Clock.fixed(OffsetDateTime.parse("2026-08-05T09:00:00Z").toInstant(), ZoneOffset.UTC));
    }

    private static Empresa empresa() {
        return Empresa.builder().id(EMPRESA).rut("76543210-9").razonSocial("Empresa")
                .giro("Pruebas").direccion("Calle 1").comuna("Quillota").build();
    }

    private static EnvioLibroResponse envio(String trackId, String periodo) {
        return new EnvioLibroResponse(1L, periodo, TipoOperacion.COMPRA, trackId, null,
                "MENSUAL", null, null, OffsetDateTime.parse("2026-08-01T10:00:00Z"));
    }
}
