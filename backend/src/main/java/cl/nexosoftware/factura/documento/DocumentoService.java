package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import cl.nexosoftware.factura.common.validation.Rut;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.folio.CafData;
import cl.nexosoftware.factura.folio.CafParser;
import cl.nexosoftware.factura.folio.FolioAsignado;
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
    private final CafParser cafParser;
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
        validarCotasDelEsquema(req);

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
                .setCaso(req.setCaso())
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
        aplicarDescuentoGlobal(doc, req.descuentoGlobalPct());

        aplicarTotales(doc);
        documentoRepository.save(doc);
        return DocumentoMapper.toResponse(doc);
    }

    @Transactional
    public DocumentoResponse emitir(Long empresaId, Long id) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        exigirEstado(doc, EstadoDte.BORRADOR, EstadoDte.FIRMADO);

        Empresa emisor = empresaService.buscar(empresaId);
        validarDatosParaSii(doc, emisor);

        // 1. Reserva atomica del folio dentro de esta transaccion. El CAF del que
        //    salio el folio viaja junto a el: su bloque <CAF> y su clave privada
        //    son los que timbran este documento.
        FolioAsignado asignado = folioService.siguienteFolio(empresaId, doc.getTipoDte());
        doc.setFolio(asignado.folio());
        CafData caf = cafParser.parsear(asignado.caf().getXmlCaf());
        if (!Rut.normalizar(caf.re()).equals(Rut.normalizar(emisor.getRut()))) {
            throw new ReglaNegocioException(
                    "El CAF del folio " + asignado.folio() + " pertenece al RUT " + caf.re()
                            + ", no al emisor (" + emisor.getRut() + ")");
        }

        // 2. Timbre (TED con FRMT real) -> XML -> firma XMLDSig -> validacion
        //    contra el XSD oficial (que exige la Signature, por eso va despues de
        //    firmar). DteInvalidoException propaga y revierte la reserva de folio
        //    (todo emitir() es una sola @Transactional).
        String ted = tedGenerator.generar(doc, emisor.getRut(), caf);
        String xml = xmlGenerator.generar(doc, emisor, ted);
        String xmlFirmado = firmaElectronica.firmar(xml);
        dteXmlValidator.validar(xmlFirmado, doc.getTipoDte());

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
     * Envia VARIOS documentos firmados en UN solo sobre EnvioDTE (un TrackID):
     * lo que exige la etapa de set de pruebas de la certificacion (un envio por
     * set). Todos deben tener XML firmado; los FIRMADO pasan a ENVIADO y todos
     * registran el TrackID del lote (un documento ya ANULADO localmente por su
     * NC conserva su estado). Si el SII no esta disponible, la excepcion
     * propaga y ningun estado cambia: se reintenta el lote completo.
     */
    @Transactional
    public LoteEnvioResponse enviarLoteSii(Long empresaId, List<Long> ids) {
        Empresa emisor = empresaService.buscar(empresaId);
        if (ids == null || ids.isEmpty()) {
            throw new ReglaNegocioException("El lote debe incluir al menos un documento");
        }
        List<DocumentoTributario> docs = new ArrayList<>();
        List<SiiGateway.EnvioSii> envios = new ArrayList<>();
        for (Long id : ids) {
            DocumentoTributario doc = buscarConDetalle(empresaId, id);
            if (doc.getXmlDte() == null) {
                throw new ReglaNegocioException(
                        "El documento " + id + " no tiene XML firmado: emitalo antes de enviar el lote");
            }
            docs.add(doc);
            envios.add(new SiiGateway.EnvioSii(
                    doc.getXmlDte(), doc.getTipoDte().getCodigo(), doc.getFolio(), emisor.getRut()));
        }

        String trackId = siiGateway.enviarLote(envios);

        List<DocumentoResumen> resumenes = new ArrayList<>();
        for (DocumentoTributario doc : docs) {
            doc.setTrackId(trackId);
            doc.setIntentosEnvio(doc.getIntentosEnvio() + 1);
            doc.setUltimoEnvioEn(OffsetDateTime.now());
            doc.setUltimoErrorEnvio(null);
            if (doc.getEstado() == EstadoDte.FIRMADO) {
                transicionar(doc, EstadoDte.ENVIADO);
            }
            documentoRepository.save(doc);
            resumenes.add(DocumentoMapper.toResumen(doc));
        }
        return new LoteEnvioResponse(trackId, resumenes);
    }

    /**
     * Reintenta el envio de un documento EN_CONTINGENCIA o RECHAZADO. Se reenvia
     * el MISMO XML firmado (el contenido del DTE es inmutable y el folio ya fue
     * consumido); ante un rechazo de fondo corresponde emitir un documento nuevo.
     * Si el SII sigue caido, el documento conserva su estado (un RECHAZADO no
     * entra a la cola de contingencia) y el motivo queda en la traza de envio.
     *
     * Antes de subir el sobre de un EN_CONTINGENCIA sin TrackID se RECONCILIA
     * por folio (ver {@link #reconciliarPorFolio}); {@code forzar} salta esa
     * reconciliacion cuando el usuario ya verifico en el portal del SII que el
     * documento no fue recibido.
     */
    @Transactional
    public DocumentoResponse reenviarSii(Long empresaId, Long id, boolean forzar) {
        DocumentoTributario doc = buscarConDetalle(empresaId, id);
        if (doc.getEstado() != EstadoDte.EN_CONTINGENCIA && doc.getEstado() != EstadoDte.RECHAZADO) {
            throw new ReglaNegocioException(
                    "Solo se puede reenviar un documento EN_CONTINGENCIA o RECHAZADO; este esta "
                            + doc.getEstado());
        }
        if (forzar || !necesitaReconciliacion(doc) || !reconciliarPorFolio(doc)) {
            intentarEnvio(doc);
        }
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
        int enContingencia = 0;
        for (Long id : ids) {
            DocumentoTributario doc = tx.execute(status -> reintentarUno(id));
            if (doc == null) {
                continue; // otro proceso lo saco de contingencia entre la consulta y el reintento
            }
            if (doc.getEstado() == EstadoDte.ENVIADO) {
                enviados++;
            }
            // Un documento puede salir de la cola sin ser reenviado (la
            // reconciliacion por folio lo encontro ACEPTADO/REPARO/RECHAZADO):
            // no es "enviado" ni sigue "en contingencia".
            if (doc.getEstado() == EstadoDte.EN_CONTINGENCIA) {
                enContingencia++;
            }
            resultados.add(DocumentoMapper.toResumen(doc));
        }
        return new ReenvioMasivoResponse(resultados.size(), enviados, enContingencia, resultados);
    }

    /**
     * Reintento de un documento del lote, dentro de una transaccion propia.
     * Un rechazo DE NEGOCIO del SII (upload rechazado, sobre invalido) saca al
     * documento de la cola (-> RECHAZADO con el motivo): dejarlo en contingencia
     * lo haria golpear al SII indefinidamente con un envio que sera rechazado
     * igual. Cualquier otro fallo inesperado se captura para que un documento
     * corrupto no aborte el resto del lote (queda EN_CONTINGENCIA con su error).
     */
    private DocumentoTributario reintentarUno(Long id) {
        DocumentoTributario doc = documentoRepository.findById(id).orElse(null);
        if (doc == null || doc.getEstado() != EstadoDte.EN_CONTINGENCIA) {
            return null;
        }
        try {
            if (!necesitaReconciliacion(doc) || !reconciliarPorFolio(doc)) {
                intentarEnvio(doc);
            }
        } catch (ReglaNegocioException | DteInvalidoException e) {
            doc.setUltimoErrorEnvio(e.getMessage());
            transicionar(doc, EstadoDte.RECHAZADO);
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
        Empresa emisor = empresaService.buscar(empresaId);
        SiiGateway.EstadoEnvio estado = siiGateway.consultarEstado(new SiiGateway.ConsultaSii(
                doc.getTrackId(), doc.getTipoDte().getCodigo(), emisor.getRut()));
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
        Empresa emisor = empresaService.buscar(doc.getEmpresaId());
        doc.setIntentosEnvio(doc.getIntentosEnvio() + 1);
        doc.setUltimoEnvioEn(OffsetDateTime.now());
        try {
            String trackId = siiGateway.enviar(new SiiGateway.EnvioSii(
                    doc.getXmlDte(), doc.getTipoDte().getCodigo(), doc.getFolio(), emisor.getRut()));
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

    /**
     * Un EN_CONTINGENCIA sin TrackID pudo haber llegado al SII aunque su
     * respuesta se perdio (timeout leyendo la respuesta del envio): antes de
     * subir el sobre otra vez hay que reconciliar por folio. Un documento CON
     * TrackID no lo necesita (su recepcion esta confirmada) y un RECHAZADO ya
     * tiene un veredicto de fondo del SII.
     */
    private static boolean necesitaReconciliacion(DocumentoTributario doc) {
        return doc.getEstado() == EstadoDte.EN_CONTINGENCIA && doc.getTrackId() == null;
    }

    /**
     * Consulta al SII el estado del DOCUMENTO por folio y, si el SII ya lo
     * conoce, adopta ese estado en vez de reenviar (evita duplicar el envio).
     *
     * @return {@code true} si el caso quedo resuelto sin reenviar (estado
     *         adoptado, folio aun en proceso, o la consulta misma no estuvo
     *         disponible); {@code false} solo ante un NO_RECIBIDO explicito,
     *         el unico veredicto que habilita subir el sobre otra vez.
     */
    private boolean reconciliarPorFolio(DocumentoTributario doc) {
        Empresa emisor = empresaService.buscar(doc.getEmpresaId());
        SiiGateway.EstadoDocumento estado;
        try {
            estado = siiGateway.consultarDocumento(new SiiGateway.ConsultaDocumento(
                    doc.getTipoDte().getCodigo(), doc.getFolio(), emisor.getRut(),
                    doc.getReceptorRut(), doc.getFechaEmision(), doc.getTotal()));
        } catch (SiiNoDisponibleException e) {
            // Sin reconciliacion no hay reenvio seguro: el documento sigue en
            // la cola con la traza del porque.
            doc.setUltimoErrorEnvio("No se pudo reconciliar el folio antes de reenviar: " + e.getMessage());
            return true;
        }
        switch (estado) {
            case NO_RECIBIDO -> {
                return false;
            }
            case ACEPTADO -> {
                doc.setUltimoErrorEnvio(null);
                transicionar(doc, EstadoDte.ACEPTADO);
            }
            case ACEPTADO_CON_REPARO -> {
                doc.setUltimoErrorEnvio(null);
                transicionar(doc, EstadoDte.REPARO);
            }
            case RECHAZADO -> {
                doc.setUltimoErrorEnvio(
                        "Reconciliado por folio: el SII ya registro este folio y lo rechazo o no lo autoriza; "
                                + "corresponde emitir un documento nuevo");
                transicionar(doc, EstadoDte.RECHAZADO);
            }
            case EN_PROCESO -> doc.setUltimoErrorEnvio(
                    "El SII ya recibio este folio y aun lo esta procesando; no se reenvia para no "
                            + "duplicarlo. Reintente mas tarde: la reconciliacion adoptara el estado final.");
            case DESCONOCIDO -> doc.setUltimoErrorEnvio(
                    "El SII conoce este folio pero su estado no es concluyente; verifiquelo en el portal "
                            + "del SII (si confirma que no fue recibido, reenvie con forzar=true)");
        }
        return true;
    }

    private static boolean esBoleta(TipoDte tipo) {
        return tipo == TipoDte.BOLETA_AFECTA || tipo == TipoDte.BOLETA_EXENTA;
    }

    /**
     * Cotas estructurales del esquema oficial, chequeadas AL CREAR para que el
     * usuario las vea de inmediato y no como un 422 de schema al emitir: maximo
     * de lineas (60 en factura/notas, 1000 en boleta — el Formato espera dividir
     * en varios documentos), maximo de 40 referencias y el rango de FechaType
     * (2000-01-01 a 2050-12-31, que ademas atrapa el tipico typo de anio).
     */
    private void validarCotasDelEsquema(CrearDocumentoRequest req) {
        int maxLineas = esBoleta(req.tipoDte()) ? 1000 : 60;
        if (req.lineas().size() > maxLineas) {
            throw new ReglaNegocioException(
                    "Una " + req.tipoDte().getDescripcion() + " admite maximo " + maxLineas
                            + " lineas (limite del esquema del SII); divida la venta en varios documentos");
        }
        if (req.referencias() != null && req.referencias().size() > 40) {
            throw new ReglaNegocioException(
                    "Un DTE admite maximo 40 referencias (limite del esquema del SII)");
        }
        if (req.fechaEmision() != null
                && (req.fechaEmision().isBefore(LocalDate.of(2000, 1, 1))
                || req.fechaEmision().isAfter(LocalDate.of(2050, 12, 31)))) {
            throw new ReglaNegocioException(
                    "La fecha de emision debe estar entre 2000-01-01 y 2050-12-31 (rango del SII)");
        }
        for (ReferenciaRequest rr : req.referencias() != null ? req.referencias() : List.<ReferenciaRequest>of()) {
            if (rr.fechaRef().isBefore(LocalDate.of(2000, 1, 1))
                    || rr.fechaRef().isAfter(LocalDate.of(2050, 12, 31))) {
                throw new ReglaNegocioException(
                        "La fecha de la referencia al documento " + rr.folioRef()
                                + " debe estar entre 2000-01-01 y 2050-12-31 (rango del SII)");
            }
        }
    }

    /**
     * Exige, antes de consumir un folio, los datos que el esquema oficial o el
     * Formato DTE del SII hacen obligatorios en la familia factura/notas y que
     * nuestro modelo permite dejar vacios: Acteco del emisor (obligatorio en el
     * XSD) y giro/direccion/comuna del receptor (obligatorios por el Formato).
     * Las boletas no los exigen (su esquema es distinto).
     */
    private void validarDatosParaSii(DocumentoTributario doc, Empresa emisor) {
        if (esBoleta(doc.getTipoDte())) {
            return;
        }
        if (emisor.getActividadEconomica() == null) {
            throw new ReglaNegocioException(
                    "La empresa no tiene codigo de actividad economica (Acteco), obligatorio para emitir "
                            + doc.getTipoDte().getDescripcion() + ". Completelo en Configuracion.");
        }
        if (esBlanco(doc.getReceptorGiro()) || esBlanco(doc.getReceptorDireccion())
                || esBlanco(doc.getReceptorComuna())) {
            throw new ReglaNegocioException(
                    "El receptor de una " + doc.getTipoDte().getDescripcion()
                            + " requiere giro, direccion y comuna (exigencia del SII). Complete la ficha del cliente.");
        }
    }

    private static boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Normaliza la cantidad a la escala del esquema (Dec12_6Type: 6 decimales,
     * minimo 0.000001, maximo 999999999999.999999). Se redondea half-up al
     * CREAR para que lo almacenado sea exactamente lo que se emite; una
     * cantidad que redondeada queda en cero (p.ej. 0.0000001) se rechaza.
     */
    private static double normalizarCantidad(Double cantidad) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(cantidad)
                .setScale(6, java.math.RoundingMode.HALF_UP);
        if (bd.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad minima por linea es 0,000001 (limite del esquema del SII)");
        }
        if (bd.compareTo(new java.math.BigDecimal("999999999999.999999")) > 0) {
            throw new ReglaNegocioException(
                    "La cantidad maxima por linea es 999.999.999.999,999999 (limite del esquema del SII)");
        }
        return bd.doubleValue();
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
        if (lr.unidad() != null && !lr.unidad().isBlank()) {
            unidad = lr.unidad();
        }

        double cantidad = normalizarCantidad(lr.cantidad());
        Double pct = normalizarPct(lr.descuentoPct(), "El descuento porcentual de la linea");
        long descuento;
        if (pct != null) {
            if (lr.descuentoMonto() != null && lr.descuentoMonto() > 0) {
                throw new ReglaNegocioException(
                        "Una linea admite descuento porcentual O en pesos, no ambos");
            }
            // El monto derivado se persiste junto al % para que XML/PDF/totales
            // usen exactamente la misma cifra.
            descuento = calculadora.descuentoPorcentual(cantidad, precio, pct);
        } else {
            descuento = lr.descuentoMonto() != null ? lr.descuentoMonto() : 0L;
        }
        long monto = calculadora.montoLinea(cantidad, precio, descuento);

        return LineaDetalle.builder()
                .productoId(productoId)
                .nombre(nombre)
                .cantidad(cantidad)
                .unidad(unidad)
                .precioUnitario(precio)
                .descuentoMonto(descuento)
                .descuentoPct(pct)
                .afecto(afecto)
                .codImpAdic(lr.codImpAdic())
                .montoLinea(monto)
                .build();
    }

    /**
     * Valida y fija el descuento global % sobre afectos (DscRcgGlobal TpoMov=D
     * TpoValor=%). Solo documentos de precios netos (el sobre de boleta usa otro
     * modelo) y con al menos una linea afecta sobre la cual aplicar la rebaja.
     */
    private void aplicarDescuentoGlobal(DocumentoTributario doc, Double pctSolicitado) {
        Double pct = normalizarPct(pctSolicitado, "El descuento global");
        if (pct == null) {
            return;
        }
        if (doc.getTipoDte().preciosBrutos()) {
            throw new ReglaNegocioException(
                    "El descuento global no esta soportado en boletas");
        }
        boolean hayAfecto = doc.getLineas().stream().anyMatch(LineaDetalle::isAfecto);
        if (!hayAfecto) {
            throw new ReglaNegocioException(
                    "El descuento global aplica sobre las lineas afectas y este documento no tiene ninguna");
        }
        doc.setDescuentoGlobalPct(pct);
    }

    /**
     * Normaliza un porcentaje de descuento a la escala del esquema (PctType: 2
     * decimales) y lo acota a (0, 100]: mas de 100% dejaria montos negativos.
     */
    private static Double normalizarPct(Double pct, String contexto) {
        if (pct == null) {
            return null;
        }
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(pct)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (bd.signum() <= 0 || bd.compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new ReglaNegocioException(contexto + " debe estar entre 0,01% y 100%");
        }
        return bd.doubleValue();
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
                doc.getLineas(), doc.getTasaIva(), doc.getTipoDte().preciosBrutos(),
                doc.getDescuentoGlobalPct());
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
