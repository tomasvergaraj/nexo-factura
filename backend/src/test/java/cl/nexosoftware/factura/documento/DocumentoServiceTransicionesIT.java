package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
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
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test de integracion (PostgreSQL real via Testcontainers) del ciclo de vida del
 * DTE en {@link DocumentoService}. Las dependencias tributarias externas (TED,
 * XML, firma y SII) se reemplazan con mocks para aislar la maquina de estados y
 * la reserva de folio del proceso criptografico/red.
 */
class DocumentoServiceTransicionesIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CafRepository cafRepository;

    @MockBean private SiiGateway siiGateway;
    @MockBean private FirmaElectronica firmaElectronica;
    @MockBean private TedGenerator tedGenerator;
    @MockBean private XmlDteGenerator xmlDteGenerator;
    // El XML mockeado ("<DTE/>") no cumple el XSD: se mockea el validador para
    // que no falle (este IT aisla la maquina de estados, no la validacion).
    @MockBean private DteXmlValidator dteXmlValidator;

    private Long empresaId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        when(tedGenerator.generar(any(), anyString())).thenReturn(new ModeloDte.Ted());
        when(xmlDteGenerator.generar(any(), any(), any())).thenReturn("<DTE/>");
        when(firmaElectronica.firmar(anyString())).thenReturn("<DTE firmado=\"true\"/>");

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("91000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa Transiciones")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("77111222-3")
                .razonSocial("Cliente de prueba")
                .build());
        clienteId = cliente.getId();

        cafRepository.save(Caf.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folioDesde(1)
                .folioHasta(1000)
                .folioActual(0)
                .agotado(false)
                .creadoEn(OffsetDateTime.now())
                .build());
    }

    @Test
    @DisplayName("emitir desde BORRADOR deja el documento FIRMADO y con folio asignado")
    void emitirDesdeBorradorFirmaYAsignaFolio() {
        DocumentoResponse borrador = crearBorrador();
        assertThat(borrador.estado()).isEqualTo(EstadoDte.BORRADOR);
        assertThat(borrador.folio()).isNull();

        DocumentoResponse emitido = documentoService.emitir(empresaId, borrador.id());

        assertThat(emitido.estado()).isEqualTo(EstadoDte.FIRMADO);
        assertThat(emitido.folio()).isNotNull();
        assertThat(emitido.folio()).isEqualTo(1L);
    }

    @Test
    @DisplayName("emitir un documento ya FIRMADO lanza ReglaNegocioException")
    void emitirEnEstadoInvalidoLanzaReglaNegocio() {
        DocumentoResponse borrador = crearBorrador();
        documentoService.emitir(empresaId, borrador.id()); // queda FIRMADO

        assertThatThrownBy(() -> documentoService.emitir(empresaId, borrador.id()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("enviar un BORRADOR (no firmado) lanza ReglaNegocioException")
    void enviarEnEstadoInvalidoLanzaReglaNegocio() {
        DocumentoResponse borrador = crearBorrador();

        assertThatThrownBy(() -> documentoService.enviarSii(empresaId, borrador.id()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("enviar desde FIRMADO deja el documento ENVIADO con trackId del SII")
    void enviarDesdeFirmadoTransicionaAEnviado() {
        when(siiGateway.enviar(anyString())).thenReturn("TRACK-123");

        DocumentoResponse borrador = crearBorrador();
        documentoService.emitir(empresaId, borrador.id()); // FIRMADO

        DocumentoResponse enviado = documentoService.enviarSii(empresaId, borrador.id());

        assertThat(enviado.estado()).isEqualTo(EstadoDte.ENVIADO);
        assertThat(enviado.trackId()).isEqualTo("TRACK-123");
    }

    private DocumentoResponse crearBorrador() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA,
                clienteId,
                null,
                "Documento de prueba",
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, null)),
                null);
        return documentoService.crear(empresaId, req);
    }
}
