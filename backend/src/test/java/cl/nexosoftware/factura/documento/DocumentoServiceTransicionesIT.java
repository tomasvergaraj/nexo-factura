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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test de integracion (PostgreSQL real via Testcontainers) del ciclo de vida del
 * DTE en {@link DocumentoService}. El unico mock es {@link SiiGateway} (red);
 * TED, XML, firma (stub del perfil de test) y validacion XSD son los reales,
 * timbrando con el CAF sintetico de {@link DteFixtures}.
 */
class DocumentoServiceTransicionesIT extends AbstractIntegrationTest {

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
        // La empresa emisora debe calzar con el RE del CAF fixture (76543210-9).
        // Como el RUT es unico en la BD compartida, se reutiliza la fila si ya
        // existe y se limpia su estado de corridas anteriores.
        empresaId = empresaEmisora("Empresa Transiciones").getId();

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
                // El rango de la fila manda para la asignacion de folios; el XML
                // del CAF fixture aporta el RE y la clave privada del timbre.
                .xmlCaf(DteFixtures.xmlCaf(33))
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
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class))).thenReturn("TRACK-123");

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
