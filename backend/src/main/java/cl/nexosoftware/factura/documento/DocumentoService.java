package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
    private final TedGenerator tedGenerator;
    private final FirmaElectronica firmaElectronica;
    private final SiiGateway siiGateway;
    private final PdfDteService pdfService;

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
        String xmlFirmado = firmaElectronica.firmar(xml);

        doc.setXmlDte(xmlFirmado);
        doc.setEstado(EstadoDte.FIRMADO);

        // 3. Si es una nota de credito que anula, transicionar el original ACEPTADO -> ANULADO
        //    en la misma transaccion (rollback atomico con la reserva del folio).
        if (doc.getTipoDte() == TipoDte.NOTA_CREDITO) {
            anularOriginalesReferenciados(doc);
        }

        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    @Transactional
    public DocumentoResponse enviarSii(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        exigirEstado(doc, EstadoDte.FIRMADO, EstadoDte.ENVIADO);

        if (doc.getXmlDte() == null) {
            throw new ReglaNegocioException("El documento no tiene XML firmado para enviar");
        }
        String trackId = siiGateway.enviar(doc.getXmlDte());
        doc.setTrackId(trackId);
        transicionar(doc, EstadoDte.ENVIADO);
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    @Transactional
    public DocumentoResponse consultarEstadoSii(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        if (doc.getTrackId() == null) {
            throw new ReglaNegocioException("El documento aun no ha sido enviado al SII");
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
                .montoLinea(monto)
                .build();
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
