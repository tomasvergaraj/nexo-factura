package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.folio.Caf;
import cl.nexosoftware.factura.folio.CafRepository;
import cl.nexosoftware.factura.tributario.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test de integracion (PostgreSQL real via Testcontainers) de la contingencia de
 * envio al SII y el reenvio de rechazados en {@link DocumentoService}: caida del
 * SII -> EN_CONTINGENCIA con traza, reintento individual y masivo, y reenvio del
 * mismo XML de un documento RECHAZADO. El unico mock es {@link SiiGateway}; la
 * emision (TED, XML, firma stub y XSD) es real con el CAF de {@link DteFixtures}.
 */
class ContingenciaReenvioIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CafRepository cafRepository;

    @MockBean private SiiGateway siiGateway;

    private Long empresaId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        // La empresa emisora debe calzar con el RE del CAF fixture (76543210-9);
        // su RUT es unico en la BD, asi que se reutiliza y se limpia su estado
        // (clave para que reenviarPendientes no arrastre documentos de otro test).
        empresaId = empresaEmisora("Empresa Contingencia").getId();

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("77111222-3")
                .razonSocial("Cliente de prueba")
                .giro("Comercio")
                .direccion("Av 2")
                .comuna("Vina")
                .build());
        clienteId = cliente.getId();

        cafRepository.save(Caf.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folioDesde(1)
                .folioHasta(1000)
                .folioActual(0)
                .agotado(false)
                .xmlCaf(DteFixtures.xmlCaf(33))
                .creadoEn(OffsetDateTime.now())
                .build());
    }

    @Test
    @DisplayName("si el SII no esta disponible, enviar deja el documento EN_CONTINGENCIA con la traza del fallo")
    void envioConSiiCaidoQuedaEnContingencia() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class)))
                .thenThrow(new SiiNoDisponibleException("SII caido"));
        DocumentoResponse doc = emitido();

        DocumentoResponse resultado = documentoService.enviarSii(empresaId, doc.id());

        assertThat(resultado.estado()).isEqualTo(EstadoDte.EN_CONTINGENCIA);
        assertThat(resultado.trackId()).isNull();
        assertThat(resultado.intentosEnvio()).isEqualTo(1);
        assertThat(resultado.ultimoEnvioEn()).isNotNull();
        assertThat(resultado.ultimoErrorEnvio()).contains("SII caido");
    }

    @Test
    @DisplayName("reenviar un documento EN_CONTINGENCIA con el SII recuperado lo deja ENVIADO y limpia el error")
    void reenvioDesdeContingenciaTransicionaAEnviado() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class)))
                .thenThrow(new SiiNoDisponibleException("SII caido"))
                .thenReturn("TRACK-77");
        DocumentoResponse doc = emitido();
        documentoService.enviarSii(empresaId, doc.id()); // EN_CONTINGENCIA

        DocumentoResponse resultado = documentoService.reenviarSii(empresaId, doc.id());

        assertThat(resultado.estado()).isEqualTo(EstadoDte.ENVIADO);
        assertThat(resultado.trackId()).isEqualTo("TRACK-77");
        assertThat(resultado.intentosEnvio()).isEqualTo(2);
        assertThat(resultado.ultimoErrorEnvio()).isNull();
    }

    @Test
    @DisplayName("si el SII sigue caido, el reintento acumula intentos y permanece EN_CONTINGENCIA")
    void reintentoConSiiCaidoPermaneceEnContingencia() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class)))
                .thenThrow(new SiiNoDisponibleException("SII caido"));
        DocumentoResponse doc = emitido();
        documentoService.enviarSii(empresaId, doc.id());

        DocumentoResponse resultado = documentoService.reenviarSii(empresaId, doc.id());

        assertThat(resultado.estado()).isEqualTo(EstadoDte.EN_CONTINGENCIA);
        assertThat(resultado.intentosEnvio()).isEqualTo(2);
    }

    @Test
    @DisplayName("reenviar-pendientes reintenta todos los EN_CONTINGENCIA y reporta el resultado")
    void reenvioMasivoProcesaTodosLosPendientes() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class)))
                .thenThrow(new SiiNoDisponibleException("SII caido"));
        DocumentoResponse doc1 = emitido();
        DocumentoResponse doc2 = emitido();
        documentoService.enviarSii(empresaId, doc1.id());
        documentoService.enviarSii(empresaId, doc2.id());

        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class))).thenReturn("TRACK-A", "TRACK-B");
        ReenvioMasivoResponse resumen = documentoService.reenviarPendientes(empresaId);

        assertThat(resumen.procesados()).isEqualTo(2);
        assertThat(resumen.enviados()).isEqualTo(2);
        assertThat(resumen.enContingencia()).isZero();
        assertThat(resumen.documentos())
                .allSatisfy(d -> assertThat(d.estado()).isEqualTo(EstadoDte.ENVIADO));
    }

    @Test
    @DisplayName("un documento RECHAZADO por el SII puede reenviarse y vuelve a ENVIADO")
    void reenvioDeRechazadoVuelveAEnviado() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class))).thenReturn("TRACK-1", "TRACK-2");
        when(siiGateway.consultarEstado(any(SiiGateway.ConsultaSii.class)))
                .thenReturn(SiiGateway.EstadoEnvio.RECHAZADO);
        DocumentoResponse doc = emitido();
        documentoService.enviarSii(empresaId, doc.id());
        DocumentoResponse rechazado = documentoService.consultarEstadoSii(empresaId, doc.id());
        assertThat(rechazado.estado()).isEqualTo(EstadoDte.RECHAZADO);

        DocumentoResponse resultado = documentoService.reenviarSii(empresaId, doc.id());

        assertThat(resultado.estado()).isEqualTo(EstadoDte.ENVIADO);
        assertThat(resultado.trackId()).isEqualTo("TRACK-2");
    }

    @Test
    @DisplayName("si el SII esta caido, el reenvio de un RECHAZADO lo deja RECHAZADO (no entra a contingencia)")
    void reenvioDeRechazadoConSiiCaidoPermaneceRechazado() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class)))
                .thenReturn("TRACK-1")
                .thenThrow(new SiiNoDisponibleException("SII caido"));
        when(siiGateway.consultarEstado(any(SiiGateway.ConsultaSii.class)))
                .thenReturn(SiiGateway.EstadoEnvio.RECHAZADO);
        DocumentoResponse doc = emitido();
        documentoService.enviarSii(empresaId, doc.id());
        documentoService.consultarEstadoSii(empresaId, doc.id()); // RECHAZADO

        DocumentoResponse resultado = documentoService.reenviarSii(empresaId, doc.id());

        // El rechazo es de fondo: el fallo transitorio queda en la traza pero el
        // documento no entra a la cola de reintento automatico.
        assertThat(resultado.estado()).isEqualTo(EstadoDte.RECHAZADO);
        assertThat(resultado.ultimoErrorEnvio()).contains("SII caido");
    }

    @Test
    @DisplayName("consultar el estado de un documento que no esta ENVIADO ni REPARO lanza ReglaNegocioException")
    void consultaEnEstadoInvalidoLanzaReglaNegocio() {
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class))).thenReturn("TRACK-1");
        when(siiGateway.consultarEstado(any(SiiGateway.ConsultaSii.class)))
                .thenReturn(SiiGateway.EstadoEnvio.RECHAZADO);
        DocumentoResponse doc = emitido();
        documentoService.enviarSii(empresaId, doc.id());
        documentoService.consultarEstadoSii(empresaId, doc.id()); // RECHAZADO, trackId queda como traza

        assertThatThrownBy(() -> documentoService.consultarEstadoSii(empresaId, doc.id()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ENVIADO");
    }

    @Test
    @DisplayName("reenviar un documento que no esta EN_CONTINGENCIA ni RECHAZADO lanza ReglaNegocioException")
    void reenvioEnEstadoInvalidoLanzaReglaNegocio() {
        DocumentoResponse doc = emitido(); // FIRMADO

        assertThatThrownBy(() -> documentoService.reenviarSii(empresaId, doc.id()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private DocumentoResponse emitido() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA,
                clienteId,
                null,
                "Documento de prueba",
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, null)),
                null);
        DocumentoResponse borrador = documentoService.crear(empresaId, req);
        return documentoService.emitir(empresaId, borrador.id());
    }

    /**
     * Busca (o crea) la empresa emisora con el RUT del CAF fixture y elimina sus
     * documentos, CAFs y clientes de tests anteriores para partir de cero.
     */
    private Empresa empresaEmisora(String razonSocial) {
        Empresa empresa = empresaRepository.findAll().stream()
                .filter(e -> DteFixtures.RUT_EMISOR.equals(e.getRut()))
                .findFirst()
                .orElseGet(() -> empresaRepository.save(Empresa.builder()
                        .rut(DteFixtures.RUT_EMISOR)
                        .razonSocial(razonSocial)
                        .giro("Pruebas")
                        .actividadEconomica(620200)
                        .direccion("Calle 1")
                        .comuna("Quillota")
                        .build()));
        Long id = empresa.getId();
        documentoRepository.deleteAll(documentoRepository.findAll().stream()
                .filter(d -> id.equals(d.getEmpresaId())).toList());
        cafRepository.deleteAll(cafRepository.findAll().stream()
                .filter(c -> id.equals(c.getEmpresaId())).toList());
        clienteRepository.deleteAll(clienteRepository.findAll().stream()
                .filter(c -> id.equals(c.getEmpresaId())).toList());
        return empresa;
    }
}
