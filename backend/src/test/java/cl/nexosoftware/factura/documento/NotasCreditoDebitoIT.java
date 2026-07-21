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
 * Test de integracion del flujo de notas de credito/debito (56/61): validacion de
 * referencias obligatorias al crear y anulacion atomica del documento original
 * ACEPTADO al emitir una nota de credito con codigo de referencia ANULA_DOCUMENTO.
 * El unico mock es {@link SiiGateway}; el timbre, el XML, la firma (stub) y la
 * validacion XSD son los reales, igual que en {@link DocumentoServiceTransicionesIT}.
 */
class NotasCreditoDebitoIT extends AbstractIntegrationTest {

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
        when(siiGateway.enviar(any(SiiGateway.EnvioSii.class))).thenReturn("TRACK-NC");
        when(siiGateway.consultarEstado(any(SiiGateway.ConsultaSii.class)))
                .thenReturn(SiiGateway.EstadoEnvio.ACEPTADO);

        // La empresa emisora debe calzar con el RE del CAF fixture (76543210-9);
        // su RUT es unico en la BD, asi que se reutiliza y se limpia su estado.
        empresaId = empresaEmisora("Empresa Notas").getId();

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("77111222-3")
                .razonSocial("Cliente de prueba")
                .giro("Comercio")
                .direccion("Av 2")
                .comuna("Vina")
                .build());
        clienteId = cliente.getId();

        cafRepository.save(caf(TipoDte.FACTURA_AFECTA));
        cafRepository.save(caf(TipoDte.NOTA_CREDITO));
    }

    @Test
    @DisplayName("crear una nota de credito sin referencias lanza ReglaNegocioException (409)")
    void crearNotaCreditoSinReferenciasFalla() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.NOTA_CREDITO,
                clienteId,
                null,
                "NC sin referencia",
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, null)),
                null);

        assertThatThrownBy(() -> documentoService.crear(empresaId, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("emitir una NC que ANULA_DOCUMENTO un original ACEPTADO lo deja ANULADO")
    void emitirNotaCreditoAnulaOriginalAceptado() {
        // 1. Factura original llevada hasta ACEPTADO.
        DocumentoResponse original = crearFactura();
        documentoService.emitir(empresaId, original.id());      // FIRMADO + folio
        documentoService.enviarSii(empresaId, original.id());   // ENVIADO
        DocumentoResponse aceptado = documentoService.consultarEstadoSii(empresaId, original.id());
        assertThat(aceptado.estado()).isEqualTo(EstadoDte.ACEPTADO);
        long folioOriginal = aceptado.folio();

        // 2. Nota de credito que anula la factura original.
        CrearDocumentoRequest ncReq = new CrearDocumentoRequest(
                TipoDte.NOTA_CREDITO,
                clienteId,
                aceptado.fechaEmision(),
                "Anulacion de factura",
                List.of(new LineaRequest(null, "Anula factura", 1.0, 10000L, null, true, null)),
                List.of(new ReferenciaRequest(
                        TipoDte.FACTURA_AFECTA.getCodigo(),
                        folioOriginal,
                        aceptado.fechaEmision(),
                        TipoReferencia.ANULA_DOCUMENTO,
                        "Anula la factura")));
        DocumentoResponse nc = documentoService.crear(empresaId, ncReq);
        assertThat(nc.referencias()).hasSize(1);

        // 3. Al emitir la NC, el original queda ANULADO en la misma transaccion.
        documentoService.emitir(empresaId, nc.id());

        DocumentoResponse originalTrasAnular = documentoService.obtener(empresaId, original.id());
        assertThat(originalTrasAnular.estado()).isEqualTo(EstadoDte.ANULADO);
    }

    private DocumentoResponse crearFactura() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA,
                clienteId,
                null,
                "Factura de prueba",
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, null)),
                null);
        return documentoService.crear(empresaId, req);
    }

    private Caf caf(TipoDte tipoDte) {
        return Caf.builder()
                .empresaId(empresaId)
                .tipoDte(tipoDte)
                .folioDesde(1)
                .folioHasta(1000)
                .folioActual(0)
                .agotado(false)
                // Fixture 33 tambien para la NC: al emitir no se cruza el TD del
                // XML con el tipo de la fila; solo importan el RE y la clave.
                .xmlCaf(DteFixtures.xmlCaf(33))
                .creadoEn(OffsetDateTime.now())
                .build();
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
