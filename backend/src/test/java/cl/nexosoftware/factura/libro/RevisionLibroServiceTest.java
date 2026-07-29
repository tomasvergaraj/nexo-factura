package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.LibroPendienteResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.libro.LibroPendiente.Estado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Revision automatica de libros: cada libro con movimiento y sin envio
 * gestionado se PREPARA (firma+validacion mockeada) y deja un marcador
 * PREPARADO; si la firma falla queda ERROR con el motivo; si no hay movimiento
 * o ya se gestiono, el marcador se borra. listarPendientes() oculta los libros
 * que ya quedaron gestionados. La firma/validacion real va mockeada.
 */
class RevisionLibroServiceTest {

    private static final Long EMPRESA = 7L;
    private static final YearMonth PERIODO = YearMonth.of(2026, 7);

    private LibroService libroService;
    private LibroEnvioService envioService;
    private EnvioLibroRepository envioLibroRepository;
    private LibroPendienteRepository pendienteRepository;
    private LibroPendienteStore store;
    private RevisionLibroService service;

    @BeforeEach
    void setUp() {
        libroService = mock(LibroService.class);
        envioService = mock(LibroEnvioService.class);
        envioLibroRepository = mock(EnvioLibroRepository.class);
        pendienteRepository = mock(LibroPendienteRepository.class);
        store = mock(LibroPendienteStore.class);
        service = new RevisionLibroService(
                libroService, envioService, envioLibroRepository, pendienteRepository, store);
    }

    @Test
    @DisplayName("revisar() prepara el libro con movimiento y sin envio: marcador PREPARADO")
    void preparaLibroConMovimiento() {
        stubConstruir(TipoOperacion.VENTA, false);
        stubConstruir(TipoOperacion.COMPRA, false);

        service.revisar(EMPRESA, PERIODO);

        // Un libro por operacion: se firma+valida (sin postear) y se guarda PREPARADO.
        verify(envioService).xmlFirmado(EMPRESA, TipoOperacion.VENTA, PERIODO, null, "MENSUAL", null);
        verify(envioService).xmlFirmado(EMPRESA, TipoOperacion.COMPRA, PERIODO, null, "MENSUAL", null);
        ArgumentCaptor<Estado> estado = ArgumentCaptor.forClass(Estado.class);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).guardar(eq(EMPRESA), eq("2026-07"), any(),
                estado.capture(), detalle.capture());
        assertThat(estado.getAllValues()).containsOnly(Estado.PREPARADO);
        assertThat(detalle.getAllValues()).containsOnlyNulls();
    }

    @Test
    @DisplayName("revisar() deja ERROR con el motivo si la firma/validacion falla")
    void marcaErrorSiLaFirmaFalla() {
        stubConstruir(TipoOperacion.VENTA, false);
        stubConstruir(TipoOperacion.COMPRA, false);
        when(envioService.xmlFirmado(eq(EMPRESA), any(), eq(PERIODO), any(), any(), any()))
                .thenThrow(new IllegalStateException("no hay CAF vigente"));

        service.revisar(EMPRESA, PERIODO);

        ArgumentCaptor<Estado> estado = ArgumentCaptor.forClass(Estado.class);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).guardar(eq(EMPRESA), eq("2026-07"), any(),
                estado.capture(), detalle.capture());
        assertThat(estado.getAllValues()).containsOnly(Estado.ERROR);
        assertThat(detalle.getAllValues()).containsOnly("no hay CAF vigente");
    }

    @Test
    @DisplayName("revisar() borra el marcador y no firma si el libro no tiene movimiento")
    void borraMarcadorSinMovimiento() {
        stubConstruir(TipoOperacion.VENTA, true);
        stubConstruir(TipoOperacion.COMPRA, true);

        service.revisar(EMPRESA, PERIODO);

        verify(store).borrar(EMPRESA, "2026-07", TipoOperacion.VENTA);
        verify(store).borrar(EMPRESA, "2026-07", TipoOperacion.COMPRA);
        verify(store, never()).guardar(any(), any(), any(), any(), any());
        verify(envioService, never()).xmlFirmado(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("revisar() borra el marcador y no firma si el libro ya fue gestionado (envio aceptado)")
    void borraMarcadorSiYaGestionado() {
        stubConstruir(TipoOperacion.VENTA, false);
        stubConstruir(TipoOperacion.COMPRA, false);
        // Un envio no-rechazado (aceptado o en vuelo) = ya gestionado.
        stubEnvios(TipoOperacion.VENTA, "ACEPTADO");
        stubEnvios(TipoOperacion.COMPRA, "ACEPTADO");

        service.revisar(EMPRESA, PERIODO);

        verify(store, times(2)).borrar(eq(EMPRESA), eq("2026-07"), any());
        verify(store, never()).guardar(any(), any(), any(), any(), any());
        verify(envioService, never()).xmlFirmado(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("revisar() sigue preparando si el unico envio fue RECHAZADO")
    void reintentaSiElEnvioFueRechazado() {
        stubConstruir(TipoOperacion.VENTA, false);
        stubConstruir(TipoOperacion.COMPRA, false);
        stubEnvios(TipoOperacion.VENTA, "RECHAZADO");
        stubEnvios(TipoOperacion.COMPRA, "RECHAZADO");

        service.revisar(EMPRESA, PERIODO);

        // Un rechazo no cuenta como gestionado: se vuelve a preparar.
        verify(store, times(2)).guardar(any(), any(), any(), any(), any());
        verify(store, never()).borrar(any(), any(), any());
    }

    @Test
    @DisplayName("revisar() con IVA de uso comun y sin factor: ERROR accionable, sin intentar firmar")
    void marcaErrorAccionableSinFactorDeProporcionalidad() {
        stubConstruir(TipoOperacion.VENTA, false);
        stubConstruirUsoComun(TipoOperacion.COMPRA, null);

        service.revisar(EMPRESA, PERIODO);

        // Ni se intenta: el generador reventaria igual, pero con un mensaje que no
        // le dice al usuario donde configurarlo.
        verify(envioService, never()).xmlFirmado(
                eq(EMPRESA), eq(TipoOperacion.COMPRA), any(), any(), any(), any());
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(store).guardar(eq(EMPRESA), eq("2026-07"), eq(TipoOperacion.COMPRA),
                eq(Estado.ERROR), detalle.capture());
        assertThat(detalle.getValue())
                .contains("IVA de uso comun")
                .contains("Configuracion");
    }

    @Test
    @DisplayName("revisar() con IVA de uso comun y factor configurado: PREPARADO normal")
    void preparaConFactorConfigurado() {
        stubConstruir(TipoOperacion.VENTA, false);
        // El factor ya viene resuelto en el libro: LibroService antepone el override
        // del parametro y, si no hay, toma el de la empresa.
        stubConstruirUsoComun(TipoOperacion.COMPRA, 0.60);

        service.revisar(EMPRESA, PERIODO);

        verify(envioService).xmlFirmado(EMPRESA, TipoOperacion.COMPRA, PERIODO, null, "MENSUAL", null);
        verify(store).guardar(EMPRESA, "2026-07", TipoOperacion.COMPRA, Estado.PREPARADO, null);
    }

    @Test
    @DisplayName("listarPendientes() oculta los libros que ya quedaron gestionados")
    void listarOcultaLosGestionados() {
        LibroPendiente ventas = marcador(1L, TipoOperacion.VENTA);
        LibroPendiente compras = marcador(2L, TipoOperacion.COMPRA);
        when(pendienteRepository.findByEmpresaIdOrderByPeriodoDescTipoOperacionAsc(EMPRESA))
                .thenReturn(List.of(ventas, compras));
        // Ventas ya tiene un envio aceptado; compras sigue pendiente.
        stubEnvios(TipoOperacion.VENTA, "ACEPTADO");
        stubEnvios(TipoOperacion.COMPRA); // sin envios

        List<LibroPendienteResponse> pendientes = service.listarPendientes(EMPRESA);

        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).id()).isEqualTo(2L);
        assertThat(pendientes.get(0).tipoOperacion()).isEqualTo(TipoOperacion.COMPRA);
        assertThat(pendientes.get(0).estado()).isEqualTo("PREPARADO");
    }

    // ---------- helpers ----------

    private void stubConstruir(TipoOperacion operacion, boolean sinMovimiento) {
        LibroResponse libro = new LibroResponse("2026-07", operacion,
                List.of(), List.of(), null, sinMovimiento, null);
        when(libroService.construir(eq(EMPRESA), eq(operacion), eq(PERIODO), any())).thenReturn(libro);
    }

    /** Libro con movimiento y con IVA de uso comun, que es cuando el XML exige FctProp. */
    private void stubConstruirUsoComun(TipoOperacion operacion, Double fctProp) {
        LibroDtos.LibroResumenTipo resumen = new LibroDtos.LibroResumenTipo(
                33, 1, 0, 100_000, 0, 19_000, 0, 0, 119_000,
                19_000, 1, fctProp == null ? 0 : Math.round(19_000 * fctProp), List.of());
        LibroResponse libro = new LibroResponse("2026-07", operacion,
                List.of(resumen), List.of(), null, false, fctProp);
        when(libroService.construir(eq(EMPRESA), eq(operacion), eq(PERIODO), any())).thenReturn(libro);
    }

    private void stubEnvios(TipoOperacion operacion, String... estados) {
        List<EnvioLibro> envios = java.util.Arrays.stream(estados)
                .map(est -> EnvioLibro.builder().empresaId(EMPRESA).periodo("2026-07")
                        .tipoOperacion(operacion).trackId("T").estado(est).build())
                .toList();
        when(envioLibroRepository.findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(
                EMPRESA, "2026-07", operacion)).thenReturn(envios);
    }

    private static LibroPendiente marcador(Long id, TipoOperacion operacion) {
        return LibroPendiente.builder()
                .id(id).empresaId(EMPRESA).periodo("2026-07").tipoOperacion(operacion)
                .estado(Estado.PREPARADO).detalle(null)
                .tmstRevision(OffsetDateTime.parse("2026-08-05T08:30:00Z"))
                .build();
    }
}
