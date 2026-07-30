package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.auth.RateLimiter;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.validation.Rut;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.tributario.PdfDteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Consulta PUBLICA de boletas: el sitio que el Formato de Boletas Electronicas
 * del SII (v2.0, pag. 5) exige al emisor autorizado — las boletas emitidas
 * disponibles para consulta por los clientes durante TRES MESES desde la
 * emision, con la URL impresa bajo el timbre electronico.
 *
 * <p>Sin autenticacion, asi que el diseno es todo-o-nada: la boleta se entrega
 * solo si coinciden EXACTOS los cinco datos que estan impresos en la propia
 * boleta (RUT emisor, tipo, folio, fecha y monto total). Cualquier diferencia
 * responde el mismo 404 sin distinguir cual campo fallo, y cada intento fallido
 * consume presupuesto del rate limiter por IP — el mismo de login/registro —
 * para que no salga gratis adivinar. Con folio+fecha+monto como llave, el
 * espacio de busqueda es impracticable a ciegas y quien tiene la boleta en la
 * mano la encuentra a la primera.
 *
 * <p>La consulta existe solo para empresas con {@code urlConsultaBoleta}
 * configurada: ese campo es el interruptor del sitio. Una empresa sin sitio
 * declarado no expone nada, ni siquiera la existencia de sus folios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultaBoletaPublicaService {

    /** Meses de disponibilidad que fija el formato del SII. */
    static final int MESES_DISPONIBLE = 3;

    private static final String NO_ENCONTRADA =
            "Boleta no encontrada. Verifique que los datos coincidan exactamente con los impresos en el documento.";

    private final DocumentoRepository documentoRepository;
    private final EmpresaRepository empresaRepository;
    private final PdfDteService pdfService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    @Transactional(readOnly = true)
    public byte[] pdf(String rutEmisor, int codigoTipo, long folio, LocalDate fecha, long total, String ip) {
        rateLimiter.verificarIp(ip);

        TipoDte tipo;
        try {
            tipo = TipoDte.desdeCodigo(codigoTipo);
        } catch (IllegalArgumentException e) {
            throw fallo(ip);
        }
        // Solo boletas: este sitio no es un canal de consulta de facturas (esas
        // se verifican en el SII) ni debe revelar nada sobre otros documentos.
        if (!tipo.esBoleta()) {
            throw fallo(ip);
        }

        Empresa emisor = empresaRepository.findByRut(Rut.normalizar(rutEmisor)).orElse(null);
        if (emisor == null || emisor.getUrlConsultaBoleta() == null
                || emisor.getUrlConsultaBoleta().isBlank()) {
            throw fallo(ip);
        }

        DocumentoTributario doc = documentoRepository
                .findByEmpresaIdAndTipoDteAndFolio(emisor.getId(), tipo, folio)
                .orElse(null);
        if (doc == null || doc.getXmlDte() == null
                || !fecha.equals(doc.getFechaEmision()) || total != doc.getTotal()) {
            throw fallo(ip);
        }

        // Ventana de tres meses del formato: fuera de ella el documento existio
        // pero ya no esta disponible en linea. El mensaje puede ser explicito:
        // quien llega aqui ya acredito tener la boleta (coincidieron los 5 datos).
        if (doc.getFechaEmision().isBefore(LocalDate.now(clock).minusMonths(MESES_DISPONIBLE))) {
            throw new RecursoNoEncontradoException(
                    "La boleta ya no esta disponible para consulta en linea: el periodo de "
                            + "publicacion es de " + MESES_DISPONIBLE + " meses desde la emision.");
        }

        // Cargar el detalle para el PDF (lineas; el fetch por identidad tributaria
        // no las trae). El documento recien se valido, asi que existe.
        DocumentoTributario conDetalle = documentoRepository.findWithDetalleById(doc.getId()).orElseThrow();
        return pdfService.generar(conDetalle, emisor);
    }

    /** 404 uniforme + consumo del presupuesto por IP: fallar no es gratis. */
    private RecursoNoEncontradoException fallo(String ip) {
        rateLimiter.registrarFalloIp(ip);
        return new RecursoNoEncontradoException(NO_ENCONTRADA);
    }
}
