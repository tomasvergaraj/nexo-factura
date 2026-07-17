package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.folio.FolioService;
import cl.nexosoftware.factura.producto.Producto;
import cl.nexosoftware.factura.producto.ProductoRepository;
import cl.nexosoftware.factura.tributario.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta el ciclo de vida del DTE: creacion del borrador, emision (asignacion
 * de folio + timbre + firma), envio al SII y consulta de estado.
 *
 * El metodo {@link #emitir} es transaccional para que la reserva del folio (que
 * usa bloqueo pesimista en {@link FolioService}) solo se confirme si el documento
 * queda efectivamente firmado.
 */
@Service
@RequiredArgsConstructor
public class DocumentoService {

    private static final double TASA_IVA = 19.0;

    // Receptor por defecto de las boletas emitidas sin cliente identificado.
    private static final String CONSUMIDOR_FINAL_RUT = "66666666-6";
    private static final String CONSUMIDOR_FINAL_RAZON = "Consumidor final";

    private final DocumentoRepository documentoRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final EmpresaService empresaService;
    private final FolioService folioService;
    private final CalculadoraImpuestos calculadora;
    private final XmlDteGenerator xmlGenerator;
    private final DteXmlValidator dteXmlValidator;
    private final TedGenerator tedGenerator;
    private final FirmaElectronica firmaElectronica;
    private final SiiGateway siiGateway;
    private final PdfDteService pdfService;
    // Para el reenvio masivo: una transaccion programatica POR documento.
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public DocumentoResponse crear(Long empresaId, CrearDocumentoRequest req) {
        empresaService.buscar(empresaId);

        // Solo las boletas (39/41) pueden emitirse sin cliente: receptor = Consumidor final.
        if (req.clienteId() == null && !esBoleta(req.tipoDte())) {
            throw new ReglaNegocioException(
                    "Solo las boletas (39/41) pueden emitirse sin cliente; "
                            + req.tipoDte().getDescripcion() + " requiere un cliente");
        }

        String receptorRut, receptorRazonSocial, receptorGiro, receptorDireccion, receptorComuna;
        if (req.clienteId() != null) {
            Cliente cliente = clienteRepository.findById(req.clienteId())
                    .filter(c -> c.getEmpresaId().equals(empresaId))
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", req.clienteId()));
            receptorRut = cliente.getRut();
            receptorRazonSocial = cliente.getRazonSocial();
            receptorGiro = cliente.getGiro();
            receptorDireccion = cliente.getDireccion();
            receptorComuna = cliente.getComuna();
        } else {
            receptorRut = CONSUMIDOR_FINAL_RUT;
            receptorRazonSocial = CONSUMIDOR_FINAL_RAZON;
            receptorGiro = null;
            receptorDireccion = null;
            receptorComuna = null;
        }

        DocumentoTributario doc = DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(req.tipoDte())
                .estado(EstadoDte.BORRADOR)
                .fechaEmision(req.fechaEmision() != null ? req.fechaEmision() : LocalDate.now())
                .receptorRut(receptorRut)
                .receptorRazonSocial(receptorRazonSocial)
                .receptorGiro(receptorGiro)
                .receptorDireccion(receptorDireccion)
                .receptorComuna(receptorComuna)
                .observacion(req.observacion())
                .tasaIva(TASA_IVA)
                .build();

        for (LineaRequest lr : req.lineas()) {
            doc.agregarLinea(construirLinea(empresaId, lr));
        }
        if (req.referencias() != null) {
            for (ReferenciaRequest rr : req.referencias()) {
                doc.agregarReferencia(Referencia.builder()
                        .tipoDocumentoRef(rr.tipoDocumentoRef())
                        .folioRef(rr.folioRef())
                        .fechaRef(rr.fechaRef())
                        .tipoReferencia(rr.tipoReferencia())
                        .razon(rr.razon())
                        .build());
            }
        }

        validarReferenciasDeNota(doc);
        validarImpuestos(doc);

        aplicarTotales(doc);
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    @Transactional
    public DocumentoResponse emitir(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        exigirEstado(doc, EstadoDte.BORRADOR, EstadoDte.FIRMADO);

        Empresa emisor = empresaService.buscar(empresaId);

        // 1. Reserva atomica del folio dentro de esta transaccion.
        long folio = folioService.siguienteFolio(empresaId, doc.getTipoDte());
        doc.setFolio(folio);

        // 2. Timbre electronico (TED) + XML + firma.
        ModeloDte.Ted ted = tedGenerator.generar(doc, emisor.getRut());
        String xml = xmlGenerator.generar(doc, emisor, ted);
        // Validacion XSD pre-firma: si el XML generado no cumple el esquema,
        // DteInvalidoException (RuntimeException) propaga y revierte la reserva de
        // folio (todo emitir() es una sola @Transactional).
        dteXmlValidator.validar(xml);
        String xmlFirmado = firmaElectronica.firmar(xml);

        doc.setXmlDte(xmlFirmado);
        // Sello de integridad (tamper-evidence) sobre el XML firmado.
        doc.setSello(SelloDte.calcular(xmlFirmado));
        doc.setEstado(EstadoDte.FIRMADO);

        // 3. Si es una nota de credito que anula, transicionar el original ACEPTADO -> ANULADO
        //    en la misma transaccion (rollback atomico con la reserva del folio).
        if (doc.getTipoDte() == TipoDte.NOTA_CREDITO) {
            anularOriginalesReferenciados(doc);
        }

        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    /**
     * Envia el documento firmado al SII. Si el SII no esta disponible, el
     * documento NO falla: pasa a EN_CONTINGENCIA (cola de reintento) y la
     * respuesta refleja ese estado con el motivo en {@code ultimoErrorEnvio}.
     */
    @Transactional
    public DocumentoResponse enviarSii(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        exigirEstado(doc, EstadoDte.FIRMADO, EstadoDte.ENVIADO);

        intentarEnvio(doc);
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    /**
     * Reintenta el envio de un documento EN_CONTINGENCIA o RECHAZADO. Se reenvia
     * el MISMO XML firmado (el contenido del DTE es inmutable y el folio ya fue
     * consumido); ante un rechazo de fondo corresponde emitir un documento nuevo.
     * Si el SII sigue caido, el documento conserva su estado (un RECHAZADO no
     * entra a la cola de contingencia) y el motivo queda en la traza de envio.
     */
    @Transactional
    public DocumentoResponse reenviarSii(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        if (doc.getEstado() != EstadoDte.EN_CONTINGENCIA && doc.getEstado() != EstadoDte.RECHAZADO) {
            throw new ReglaNegocioException(
                    "Solo se puede reenviar un documento EN_CONTINGENCIA o RECHAZADO; este esta "
                            + doc.getEstado());
        }
        intentarEnvio(doc);
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    /**
     * Reintenta el envio de TODOS los documentos EN_CONTINGENCIA de la empresa,
     * del mas antiguo al mas nuevo. Cada documento se procesa en SU PROPIA
     * transaccion: un TrackID ya aceptado por el SII queda confirmado aunque un
     * documento posterior del lote falle (sin esto, un rollback del lote
     * revertiria envios que el SII ya recibio y el proximo reintento los
     * duplicaria). Los que fallan quedan EN_CONTINGENCIA con su error.
     */
    public ReenvioMasivoResponse reenviarPendientes(Long empresaId) {
        empresaService.buscar(empresaId);
        List<Long> ids = documentoRepository
                .findIdsByEmpresaIdAndEstado(empresaId, EstadoDte.EN_CONTINGENCIA);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<DocumentoResumen> resultados = new ArrayList<>();
        int enviados = 0;
        for (Long id : ids) {
            DocumentoTributario doc = tx.execute(status -> reintentarUno(id));
            if (doc == null) {
                continue; // otro proceso lo saco de contingencia entre la consulta y el reintento
            }
            if (doc.getEstado() == EstadoDte.ENVIADO) {
                enviados++;
            }
            resultados.add(DocumentoMapper.toResumen(doc));
        }
        return new ReenvioMasivoResponse(resultados.size(), enviados, resultados.size() - enviados, resultados);
    }

    /**
     * Reintento de un documento del lote, dentro de una transaccion propia.
     * Captura cualquier fallo inesperado (no solo la caida del SII) para que un
     * documento corrupto no aborte el resto del lote.
     */
    private DocumentoTributario reintentarUno(Long id) {
        DocumentoTributario doc = documentoRepository.findById(id).orElse(null);
        if (doc == null || doc.getEstado() != EstadoDte.EN_CONTINGENCIA) {
            return null;
        }
        try {
            intentarEnvio(doc);
        } catch (RuntimeException e) {
            doc.setUltimoErrorEnvio(e.getMessage());
        }
        return documentoRepository.save(doc);
    }

    @Transactional
    public DocumentoResponse consultarEstadoSii(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        if (doc.getTrackId() == null) {
            throw new ReglaNegocioException("El documento aun no ha sido enviado al SII");
        }
        // El TrackID puede quedar como traza en estados posteriores (RECHAZADO,
        // ACEPTADO); consultar solo tiene sentido con un envio en curso.
        if (doc.getEstado() != EstadoDte.ENVIADO && doc.getEstado() != EstadoDte.REPARO) {
            throw new ReglaNegocioException(
                    "Solo se puede consultar el estado de un documento ENVIADO o en REPARO; este esta "
                            + doc.getEstado());
        }
        SiiGateway.EstadoEnvio estado = siiGateway.consultarEstado(doc.getTrackId());
        switch (estado) {
            case ACEPTADO -> transicionar(doc, EstadoDte.ACEPTADO);
            case ACEPTADO_CON_REPARO -> transicionar(doc, EstadoDte.REPARO);
            case RECHAZADO -> transicionar(doc, EstadoDte.RECHAZADO);
            case RECIBIDO -> { /* sigue en ENVIADO */ }
        }
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    @Transactional(readOnly = true)
    public byte[] generarPdf(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        Empresa emisor = empresaService.buscar(empresaId);
        return pdfService.generar(doc, emisor);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentoResumen> listar(Long empresaId, EstadoDte estado, Pageable pageable) {
        var page = (estado != null)
                ? documentoRepository.findByEmpresaIdAndEstadoOrderByCreadoEnDesc(empresaId, estado, pageable)
                : documentoRepository.findByEmpresaIdOrderByCreadoEnDesc(empresaId, pageable);
        return PageResponse.de(page.map(DocumentoMapper::toResumen));
    }

    @Transactional(readOnly = true)
    public DocumentoResponse obtener(Long empresaId, Long id) {
        return DocumentoMapper.toResponse(buscarConDetalle(empresaId, id));
    }

    // ---------- helpers de dominio ----------

    /**
     * Un intento de envio al SII. Exito: TrackID nuevo y transicion a ENVIADO.
     * SII no disponible: el motivo queda registrado y solo un documento FIRMADO
     * pasa a EN_CONTINGENCIA (entra a la cola de reintento); un RECHAZADO
     * permanece RECHAZADO (el rechazo del SII es de fondo, no transitorio) y un
     * EN_CONTINGENCIA sigue en cola. En todos los casos queda la traza
     * (intentos y timestamp).
     */
    private void intentarEnvio(DocumentoTributario doc) {
        if (doc.getXmlDte() == null) {
            throw new ReglaNegocioException("El documento no tiene XML firmado para enviar");
        }
        doc.setIntentosEnvio(doc.getIntentosEnvio() + 1);
        doc.setUltimoEnvioEn(OffsetDateTime.now());
        try {
            String trackId = siiGateway.enviar(doc.getXmlDte());
            doc.setTrackId(trackId);
            doc.setUltimoErrorEnvio(null);
            transicionar(doc, EstadoDte.ENVIADO);
        } catch (SiiNoDisponibleException e) {
            doc.setUltimoErrorEnvio(e.getMessage());
            if (doc.getEstado() == EstadoDte.FIRMADO) {
                transicionar(doc, EstadoDte.EN_CONTINGENCIA);
            }
        }
    }

    private static boolean esBoleta(TipoDte tipo) {
        return tipo == TipoDte.BOLETA_AFECTA || tipo == TipoDte.BOLETA_EXENTA;
    }

    private LineaDetalle construirLinea(Long empresaId, LineaRequest lr) {
        String nombre = lr.nombre();
        long precio = lr.precioUnitario() != null ? lr.precioUnitario() : 0L;
        boolean afecto = lr.afecto() != null ? lr.afecto() : true;
        String unidad = "UN";
        Long productoId = null;

        if (lr.productoId() != null) {
            Producto p = productoRepository.findById(lr.productoId())
                    .filter(prod -> prod.getEmpresaId().equals(empresaId))
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", lr.productoId()));
            productoId = p.getId();
            if (nombre == null) nombre = p.getNombre();
            if (lr.precioUnitario() == null) precio = p.getPrecioNeto();
            if (lr.afecto() == null) afecto = p.isAfecto();
            unidad = p.getUnidad();
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Cada linea requiere un producto o un nombre");
        }

        long descuento = lr.descuentoMonto() != null ? lr.descuentoMonto() : 0L;
        long monto = calculadora.montoLinea(lr.cantidad(), precio, descuento);

        return LineaDetalle.builder()
                .productoId(productoId)
                .nombre(nombre)
                .cantidad(lr.cantidad())
                .unidad(unidad)
                .precioUnitario(precio)
                .descuentoMonto(descuento)
                .afecto(afecto)
                .codImpAdic(lr.codImpAdic())
                .montoLinea(monto)
                .build();
    }

    /**
     * Valida los otros impuestos (P1-6). Solo se admiten en documentos de precios
     * netos y afectos (factura afecta 33, notas 56/61), sobre lineas afectas, y con
     * un codigo presente en el catalogo {@link TipoImpuesto}. Cualquier otro caso es
     * regla de negocio violada (-&gt; 409).
     */
    private void validarImpuestos(DocumentoTributario doc) {
        boolean permitido = !doc.getTipoDte().preciosBrutos() && doc.getTipoDte().esAfecto();
        for (LineaDetalle l : doc.getLineas()) {
            Integer cod = l.getCodImpAdic();
            if (cod == null) {
                continue;
            }
            if (!permitido) {
                throw new ReglaNegocioException(
                        "Los impuestos adicionales/retenciones no se admiten en "
                                + doc.getTipoDte().getDescripcion());
            }
            if (!l.isAfecto()) {
                throw new ReglaNegocioException("Un impuesto adicional solo aplica a lineas afectas");
            }
            if (!TipoImpuesto.existe(cod)) {
                throw new ReglaNegocioException("Codigo de impuesto desconocido: " + cod);
            }
        }
    }

    /**
     * Valida que las notas de credito/debito (56/61) referencien al menos un
     * documento original coherente: existente, no borrador, distinto de la nota y
     * con la misma fecha de emision registrada en la referencia.
     */
    private void validarReferenciasDeNota(DocumentoTributario doc) {
        if (doc.getTipoDte() != TipoDte.NOTA_CREDITO && doc.getTipoDte() != TipoDte.NOTA_DEBITO) {
            return;
        }
        if (doc.getReferencias().isEmpty()) {
            throw new ReglaNegocioException(
                    "Una " + doc.getTipoDte().getDescripcion() + " debe referenciar al menos un documento");
        }
        for (Referencia ref : doc.getReferencias()) {
            DocumentoTributario original = localizarReferenciado(doc.getEmpresaId(), ref);
            if (original.getId() != null && original.getId().equals(doc.getId())) {
                throw new ReglaNegocioException("Una nota no puede referenciarse a si misma");
            }
            if (original.getEstado() == EstadoDte.BORRADOR) {
                throw new ReglaNegocioException(
                        "El documento referenciado aun esta en borrador y no puede ser corregido");
            }
            if (!original.getFechaEmision().equals(ref.getFechaRef())) {
                throw new ReglaNegocioException(
                        "La fecha de la referencia no coincide con la del documento original");
            }
        }
    }

    /**
     * Anula los documentos originales referenciados por una nota de credito con
     * codigo de referencia ANULA_DOCUMENTO. Solo un documento ACEPTADO es anulable.
     */
    private void anularOriginalesReferenciados(DocumentoTributario doc) {
        for (Referencia ref : doc.getReferencias()) {
            if (ref.getTipoReferencia() != TipoReferencia.ANULA_DOCUMENTO) {
                continue;
            }
            DocumentoTributario original = localizarReferenciado(doc.getEmpresaId(), ref);
            if (original.getEstado() != EstadoDte.ACEPTADO) {
                throw new ReglaNegocioException(
                        "Solo se puede anular un documento ACEPTADO; el documento referenciado esta en estado "
                                + original.getEstado());
            }
            transicionar(original, EstadoDte.ANULADO);
            documentoRepository.save(original);
        }
    }

    private DocumentoTributario localizarReferenciado(Long empresaId, Referencia ref) {
        TipoDte tipoRef = TipoDte.desdeCodigo(ref.getTipoDocumentoRef());
        return documentoRepository
                .findByEmpresaIdAndTipoDteAndFolio(empresaId, tipoRef, ref.getFolioRef())
                .orElseThrow(() -> new ReglaNegocioException("No existe el documento referenciado"));
    }

    private void aplicarTotales(DocumentoTributario doc) {
        var t = calculadora.calcular(
                doc.getLineas(), doc.getTasaIva(), doc.getTipoDte().preciosBrutos());
        doc.setNeto(t.neto());
        doc.setExento(t.exento());
        doc.setIva(t.iva());
        doc.setImpuestosAdicionales(t.impuestosAdicionales());
        doc.setIvaRetenido(t.ivaRetenido());
        doc.setTotal(t.total());
    }

    private DocumentoTributario buscarConDetalle(Long empresaId, Long id) {
        DocumentoTributario doc = documentoRepository.findWithDetalleById(id)
                .filter(d -> d.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Documento", id));
        return doc;
    }

    private void exigirEstado(DocumentoTributario doc, EstadoDte esperado, EstadoDte destino) {
        if (doc.getEstado() != esperado) {
            throw new ReglaNegocioException(
                    "El documento esta en estado " + doc.getEstado() + " y no puede pasar a " + destino);
        }
    }

    private void transicionar(DocumentoTributario doc, EstadoDte destino) {
        if (!doc.getEstado().puedeTransicionarA(destino)) {
            throw new ReglaNegocioException(
                    "Transicion invalida: " + doc.getEstado() + " -> " + destino);
        }
        doc.setEstado(destino);
    }
}
