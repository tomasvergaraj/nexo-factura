package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.libro.LibroDtos.EnvioLibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroEnvioResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.FirmaElectronica;
import cl.nexosoftware.factura.tributario.LibroXmlGenerator;
import cl.nexosoftware.factura.tributario.ResolucionResolver;
import cl.nexosoftware.factura.tributario.SiiGateway;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comportamiento de persistencia del envio de libros: enviar() deja un registro
 * con el TrackID, estadoEnvio() actualiza el estado del registro con lo que
 * responde el SII, y listar() mapea los envios del periodo. La firma/validacion/
 * envio al SII van mockeados: aca solo importa lo que se persiste.
 */
class LibroEnvioServiceTest {

    private static final Long EMPRESA = 7L;
    private static final YearMonth PERIODO = YearMonth.of(2026, 7);

    private LibroService libroService;
    private LibroXmlGenerator xmlGenerator;
    private FirmaElectronica firma;
    private DteXmlValidator validator;
    private SiiGateway siiGateway;
    private EmpresaService empresaService;
    private ResolucionResolver resolucionResolver;
    private CertificadoResolver certificadoResolver;
    private EnvioLibroRepository envioLibroRepository;
    private LibroEnvioService service;

    @BeforeEach
    void setUp() {
        libroService = mock(LibroService.class);
        xmlGenerator = mock(LibroXmlGenerator.class);
        firma = mock(FirmaElectronica.class);
        validator = mock(DteXmlValidator.class);
        siiGateway = mock(SiiGateway.class);
        empresaService = mock(EmpresaService.class);
        resolucionResolver = mock(ResolucionResolver.class);
        certificadoResolver = mock(CertificadoResolver.class);
        envioLibroRepository = mock(EnvioLibroRepository.class);
        service = new LibroEnvioService(libroService, xmlGenerator, firma, validator,
                siiGateway, empresaService, resolucionResolver, certificadoResolver,
                envioLibroRepository);
    }

    @Test
    @DisplayName("enviar() persiste el envio con el TrackID del SII")
    void enviarPersisteElEnvio() {
        stubFirmaYEnvio("TRACK-123");

        LibroEnvioResponse resp = service.enviar(EMPRESA, TipoOperacion.VENTA, PERIODO,
                null, "MENSUAL", null);

        assertThat(resp.trackId()).isEqualTo("TRACK-123");
        assertThat(resp.periodo()).isEqualTo("2026-07");
        assertThat(resp.tipoOperacion()).isEqualTo(TipoOperacion.VENTA);

        ArgumentCaptor<EnvioLibro> captor = ArgumentCaptor.forClass(EnvioLibro.class);
        verify(envioLibroRepository).save(captor.capture());
        EnvioLibro guardado = captor.getValue();
        assertThat(guardado.getEmpresaId()).isEqualTo(EMPRESA);
        assertThat(guardado.getPeriodo()).isEqualTo("2026-07");
        assertThat(guardado.getTipoOperacion()).isEqualTo(TipoOperacion.VENTA);
        assertThat(guardado.getTrackId()).isEqualTo("TRACK-123");
        assertThat(guardado.getTipoLibro()).isEqualTo("MENSUAL");
        assertThat(guardado.getFolioNotificacion()).isNull();
        // El estado aun no se consulta: arranca nulo (llega despues por QueryEstUp).
        assertThat(guardado.getEstado()).isNull();
    }

    @Test
    @DisplayName("enviar() propaga tipoLibro ESPECIAL y el folio de notificacion (certificacion)")
    void enviarPropagaLibroEspecial() {
        stubFirmaYEnvio("TRACK-ESP");

        service.enviar(EMPRESA, TipoOperacion.COMPRA, PERIODO, null, "ESPECIAL", 4965879L);

        ArgumentCaptor<EnvioLibro> captor = ArgumentCaptor.forClass(EnvioLibro.class);
        verify(envioLibroRepository).save(captor.capture());
        EnvioLibro guardado = captor.getValue();
        assertThat(guardado.getTipoLibro()).isEqualTo("ESPECIAL");
        assertThat(guardado.getFolioNotificacion()).isEqualTo(4965879L);
        assertThat(guardado.getTipoOperacion()).isEqualTo(TipoOperacion.COMPRA);
    }

    @Test
    @DisplayName("estadoEnvio() guarda en el registro el estado que responde el SII")
    void estadoEnvioActualizaElRegistro() {
        Empresa emisor = mock(Empresa.class);
        when(emisor.getRut()).thenReturn("78397017-1");
        when(empresaService.buscar(EMPRESA)).thenReturn(emisor);
        when(siiGateway.consultarEstado(any())).thenReturn(SiiGateway.EstadoEnvio.ACEPTADO);
        EnvioLibro registro = EnvioLibro.builder().empresaId(EMPRESA).trackId("TRACK-9").build();
        when(envioLibroRepository.findFirstByEmpresaIdAndTrackId(EMPRESA, "TRACK-9"))
                .thenReturn(Optional.of(registro));

        SiiGateway.EstadoEnvio estado = service.estadoEnvio(EMPRESA, "TRACK-9");

        assertThat(estado).isEqualTo(SiiGateway.EstadoEnvio.ACEPTADO);
        // El estado queda en la entidad gestionada (JPA hace el flush por dirty checking).
        assertThat(registro.getEstado()).isEqualTo("ACEPTADO");
    }

    @Test
    @DisplayName("estadoEnvio() no falla si el TrackID no esta registrado (envio por Swagger)")
    void estadoEnvioSinRegistroNoFalla() {
        Empresa emisor = mock(Empresa.class);
        when(emisor.getRut()).thenReturn("78397017-1");
        when(empresaService.buscar(EMPRESA)).thenReturn(emisor);
        when(siiGateway.consultarEstado(any())).thenReturn(SiiGateway.EstadoEnvio.RECIBIDO);
        when(envioLibroRepository.findFirstByEmpresaIdAndTrackId(EMPRESA, "TRACK-X"))
                .thenReturn(Optional.empty());

        assertThat(service.estadoEnvio(EMPRESA, "TRACK-X"))
                .isEqualTo(SiiGateway.EstadoEnvio.RECIBIDO);
    }

    @Test
    @DisplayName("listar() mapea los envios del periodo, del mas reciente al mas antiguo")
    void listarMapeaLosEnvios() {
        EnvioLibro e = EnvioLibro.builder()
                .id(11L).empresaId(EMPRESA).periodo("2026-07")
                .tipoOperacion(TipoOperacion.VENTA).trackId("TRACK-1")
                .estado("ACEPTADO").tipoLibro("MENSUAL").folioNotificacion(null)
                .tmstEnvio(OffsetDateTime.parse("2026-08-01T10:15:00Z"))
                .build();
        when(envioLibroRepository.findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(
                EMPRESA, "2026-07", TipoOperacion.VENTA)).thenReturn(List.of(e));

        List<EnvioLibroResponse> envios = service.listar(EMPRESA, TipoOperacion.VENTA, PERIODO);

        assertThat(envios).hasSize(1);
        EnvioLibroResponse r = envios.get(0);
        assertThat(r.id()).isEqualTo(11L);
        assertThat(r.trackId()).isEqualTo("TRACK-1");
        assertThat(r.estado()).isEqualTo("ACEPTADO");
        assertThat(r.tipoLibro()).isEqualTo("MENSUAL");
        assertThat(r.tmstEnvio()).isEqualTo(OffsetDateTime.parse("2026-08-01T10:15:00Z"));
        // Listar no toca al SII.
        verifyNoInteractions(siiGateway);
    }

    /** Stubs del camino feliz de firma + envio; el libro tiene movimiento. */
    private void stubFirmaYEnvio(String trackId) {
        Empresa emisor = mock(Empresa.class);
        when(emisor.getRut()).thenReturn("78397017-1");
        when(empresaService.buscar(EMPRESA)).thenReturn(emisor);
        LibroResponse libro = new LibroResponse("2026-07", TipoOperacion.VENTA,
                List.of(), List.of(), null, false, null);
        when(libroService.construir(eq(EMPRESA), any(), eq(PERIODO), any())).thenReturn(libro);
        when(resolucionResolver.paraCaratula(EMPRESA))
                .thenReturn(new ResolucionResolver.Resolucion("2026-01-01", 80));
        when(certificadoResolver.paraEmpresaSiExiste(EMPRESA)).thenReturn(Optional.empty());
        when(xmlGenerator.generar(any(), any(), any())).thenReturn("<LibroCompraVenta/>");
        when(firma.firmarEnveloped(any(), eq(LibroXmlGenerator.ID_ENVIO_LIBRO), eq(EMPRESA)))
                .thenReturn("<LibroCompraVenta firmado=\"1\"/>");
        when(siiGateway.enviarLibro(any())).thenReturn(trackId);
    }
}
