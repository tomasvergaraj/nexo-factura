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
import cl.nexosoftware.factura.tributario.FirmaElectronica;
import cl.nexosoftware.factura.tributario.SelloDte;
import cl.nexosoftware.factura.tributario.SiiGateway;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test de integracion que emite documentos REALES con el {@link cl.nexosoftware.factura.tributario.XmlDteGenerator},
 * {@link cl.nexosoftware.factura.tributario.TedGenerator} y {@link cl.nexosoftware.factura.tributario.DteXmlValidator}
 * reales (solo se mockean firma y SII). Garantiza que cada DTE legitimo que el
 * sistema genera supera la validacion XSD pre-firma, incluido el bloque
 * Referencia de las notas de credito.
 */
class EmisionXsdIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CafRepository cafRepository;

    // Firma y SII se mockean; XmlDteGenerator/TedGenerator/DteXmlValidator son reales.
    @MockBean private FirmaElectronica firmaElectronica;
    @MockBean private SiiGateway siiGateway;

    private Long empresaId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        // Passthrough: el XML firmado guardado es el mismo XML que ya paso el XSD.
        when(firmaElectronica.firmar(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(siiGateway.enviar(anyString())).thenReturn("TRACK-XSD");
        when(siiGateway.consultarEstado(anyString())).thenReturn(SiiGateway.EstadoEnvio.ACEPTADO);

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("91000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa XSD")
                .giro("Servicios")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("77111222-3")
                .razonSocial("Cliente de prueba")
                .giro("Comercio")
                .build());
        clienteId = cliente.getId();

        cafRepository.save(caf(TipoDte.FACTURA_AFECTA));
        cafRepository.save(caf(TipoDte.NOTA_CREDITO));
        cafRepository.save(caf(TipoDte.BOLETA_AFECTA));
        cafRepository.save(caf(TipoDte.BOLETA_EXENTA));
    }

    @Test
    @DisplayName("emitir una factura afecta produce un XML que pasa el XSD")
    void emitirFacturaAfectaProduceXmlValido() {
        DocumentoResponse factura = crearFactura();
        DocumentoResponse emitido = documentoService.emitir(empresaId, factura.id());

        assertThat(emitido.estado()).isEqualTo(EstadoDte.FIRMADO);
        String xml = xmlDe(factura.id());
        assertThat(xml).contains("<TipoDTE>33</TipoDTE>").contains("<TED").contains("<FRMT");
    }

    @Test
    @DisplayName("emitir una boleta a consumidor final produce un XML valido sin GiroRecep")
    void emitirBoletaConsumidorFinalProduceXmlValido() {
        DocumentoResponse boleta = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.BOLETA_AFECTA, null, null, null,
                List.of(new LineaRequest(null, "Cafe", 1.0, 11900L, null, true, null)),
                null));
        documentoService.emitir(empresaId, boleta.id());

        String xml = xmlDe(boleta.id());
        assertThat(xml).contains("<RUTRecep>66666666-6</RUTRecep>");
        assertThat(xml).doesNotContain("<GiroRecep>");
    }

    @Test
    @DisplayName("emitir una boleta exenta produce un XML valido con IndExe e IVA cero")
    void emitirBoletaExentaProduceXmlValido() {
        DocumentoResponse boleta = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.BOLETA_EXENTA, null, null, null,
                List.of(new LineaRequest(null, "Servicio exento", 1.0, 8000L, null, false, null)),
                null));
        documentoService.emitir(empresaId, boleta.id());

        String xml = xmlDe(boleta.id());
        assertThat(xml).contains("<IndExe>1</IndExe>").contains("<IVA>0</IVA>").contains("<MntExe>8000</MntExe>");
    }

    @Test
    @DisplayName("emitir una nota de credito incluye un bloque Referencia que pasa el XSD")
    void emitirNotaCreditoIncluyeBloqueReferenciaValido() {
        DocumentoResponse original = crearFactura();
        documentoService.emitir(empresaId, original.id());
        documentoService.enviarSii(empresaId, original.id());
        DocumentoResponse aceptado = documentoService.consultarEstadoSii(empresaId, original.id());

        CrearDocumentoRequest ncReq = new CrearDocumentoRequest(
                TipoDte.NOTA_CREDITO, clienteId, aceptado.fechaEmision(), "Anula factura",
                List.of(new LineaRequest(null, "Anula factura", 1.0, 10000L, null, true, null)),
                List.of(new ReferenciaRequest(
                        TipoDte.FACTURA_AFECTA.getCodigo(),
                        aceptado.folio(),
                        aceptado.fechaEmision(),
                        TipoReferencia.ANULA_DOCUMENTO,
                        "Anula la factura")));
        DocumentoResponse nc = documentoService.crear(empresaId, ncReq);
        documentoService.emitir(empresaId, nc.id());

        String xml = xmlDe(nc.id());
        assertThat(xml).contains("<Referencia>")
                .contains("<TpoDocRef>33</TpoDocRef>")
                .contains("<CodRef>1</CodRef>");
    }

    @Test
    @DisplayName("emitir una factura con impuesto adicional y retencion produce ImptoReten valido")
    void emitirFacturaConOtrosImpuestosProduceXmlValido() {
        DocumentoResponse factura = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, clienteId, null, "Factura con otros impuestos",
                List.of(
                        new LineaRequest(null, "Cerveza", 1.0, 100000L, null, true, 26),   // ILA 20,5%
                        new LineaRequest(null, "Servicio cambio sujeto", 1.0, 50000L, null, true, 15)), // retencion 19%
                null));

        // neto=150000, iva=28500, adicional=round(100000*20,5%)=20500, retenido=round(50000*19%)=9500
        assertThat(factura.impuestosAdicionales()).isEqualTo(20500);
        assertThat(factura.ivaRetenido()).isEqualTo(9500);
        assertThat(factura.total()).isEqualTo(150000 + 28500 + 20500 - 9500); // 189500
        assertThat(factura.impuestos()).hasSize(2);

        documentoService.emitir(empresaId, factura.id());
        String xml = xmlDe(factura.id());
        assertThat(xml)
                .contains("<ImptoReten>")
                .contains("<TipoImp>26</TipoImp>").contains("<TipoImp>15</TipoImp>")
                .contains("<CodImpAdic>26</CodImpAdic>").contains("<CodImpAdic>15</CodImpAdic>");

        // El total persistido (y por ende el MNT del TED) refleja adicionales y retencion.
        DocumentoTributario emitido = documentoRepository.findById(factura.id()).orElseThrow();
        assertThat(emitido.getTotal()).isEqualTo(189500);
    }

    @Test
    @DisplayName("una boleta con codigo de otro impuesto es rechazada (solo facturas/notas)")
    void boletaConOtroImpuestoEsRechazada() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.BOLETA_AFECTA, null, null, null,
                List.of(new LineaRequest(null, "Cerveza", 1.0, 11900L, null, true, 26)),
                null);
        assertThatThrownBy(() -> documentoService.crear(empresaId, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("un otro impuesto en una linea exenta es rechazado")
    void otroImpuestoEnLineaExentaEsRechazado() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, clienteId, null, null,
                List.of(new LineaRequest(null, "Exento", 1.0, 10000L, null, false, 26)),
                null);
        assertThatThrownBy(() -> documentoService.crear(empresaId, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("un codigo de otro impuesto desconocido es rechazado")
    void codigoDeImpuestoDesconocidoEsRechazado() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, clienteId, null, null,
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, 999)),
                null);
        assertThatThrownBy(() -> documentoService.crear(empresaId, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("emitir fija un sello de integridad que corresponde al XML firmado")
    void emitirFijaSelloDeIntegridad() {
        DocumentoResponse factura = crearFactura();
        documentoService.emitir(empresaId, factura.id());

        DocumentoTributario emitido = documentoRepository.findById(factura.id()).orElseThrow();
        assertThat(emitido.getSello()).hasSize(64);
        assertThat(emitido.getSello()).isEqualTo(SelloDte.calcular(emitido.getXmlDte()));
    }

    @Test
    @DisplayName("los montos de un DTE son inmutables (updatable=false los congela)")
    void montosDelDteSonInmutables() {
        DocumentoResponse factura = crearFactura();
        documentoService.emitir(empresaId, factura.id());

        // Intentar mutar el neto y persistir: updatable=false lo excluye del UPDATE.
        DocumentoTributario emitido = documentoRepository.findById(factura.id()).orElseThrow();
        long netoOriginal = emitido.getNeto();
        emitido.setNeto(netoOriginal + 999_999);
        documentoRepository.save(emitido);

        DocumentoTributario recargado = documentoRepository.findById(factura.id()).orElseThrow();
        assertThat(recargado.getNeto()).isEqualTo(netoOriginal);
    }

    private DocumentoResponse crearFactura() {
        return documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, clienteId, null, "Factura de prueba",
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true, null)),
                null));
    }

    private String xmlDe(Long id) {
        return documentoRepository.findById(id).orElseThrow().getXmlDte();
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
