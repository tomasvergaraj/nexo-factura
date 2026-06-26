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
 * Test de integracion del flujo de notas de credito/debito (56/61): validacion de
 * referencias obligatorias al crear y anulacion atomica del documento original
 * ACEPTADO al emitir una nota de credito con codigo de referencia ANULA_DOCUMENTO.
 * Las dependencias tributarias externas (TED, XML, firma y SII) se reemplazan con
 * mocks, igual que en {@link DocumentoServiceTransicionesIT}.
 */
class NotasCreditoDebitoIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CafRepository cafRepository;

    @MockBean private SiiGateway siiGateway;
    @MockBean private FirmaElectronica firmaElectronica;
    @MockBean private TedGenerator tedGenerator;
    @MockBean private XmlDteGenerator xmlDteGenerator;
    // El XML mockeado ("<DTE/>") no cumple el XSD: se mockea el validador para
    // que no falle (este IT aisla las notas, no la validacion de esquema).
    @MockBean private DteXmlValidator dteXmlValidator;

    private Long empresaId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        when(tedGenerator.generar(any(), anyString())).thenReturn(new ModeloDte.Ted());
        when(xmlDteGenerator.generar(any(), any(), any())).thenReturn("<DTE/>");
        when(firmaElectronica.firmar(anyString())).thenReturn("<DTE firmado=\"true\"/>");
        when(siiGateway.enviar(anyString())).thenReturn("TRACK-NC");
        when(siiGateway.consultarEstado(anyString())).thenReturn(SiiGateway.EstadoEnvio.ACEPTADO);

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("91000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa Notas")
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
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true)),
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
                List.of(new LineaRequest(null, "Anula factura", 1.0, 10000L, null, true)),
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
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true)),
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
                .creadoEn(OffsetDateTime.now())
                .build();
    }
}
