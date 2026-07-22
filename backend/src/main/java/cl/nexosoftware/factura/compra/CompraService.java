package cl.nexosoftware.factura.compra;

import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.validation.Rut;
import cl.nexosoftware.factura.compra.CompraDtos.*;
import cl.nexosoftware.factura.empresa.EmpresaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Registro manual de documentos recibidos (compras) para el libro de compras.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    /**
     * Tipos de documento recibido admitidos: facturas de papel (30/32) y
     * electronicas (33/34), facturas de compra (45/46) y notas de debito/credito
     * de papel (55/60) y electronicas (56/61) — los tipos que exige el libro de
     * compras del set de certificacion del SII.
     */
    static final Set<Integer> TIPOS_PERMITIDOS = Set.of(30, 32, 33, 34, 45, 46, 55, 56, 60, 61);

    /** Codigos de IVA no recuperable del catalogo del SII. */
    static final Set<Integer> CODIGOS_IVA_NO_REC = Set.of(1, 2, 3, 4, 9);

    private final CompraRepository compraRepository;
    private final EmpresaService empresaService;

    @Transactional
    public CompraResponse crear(Long empresaId, CompraRequest req) {
        empresaService.buscar(empresaId);
        validar(req);

        DocumentoCompra compra = DocumentoCompra.builder()
                .empresaId(empresaId)
                .tipoDte(req.tipoDte())
                .folio(req.folio())
                .rutProveedor(Rut.normalizar(req.rutProveedor()))
                .razonSocial(req.razonSocial())
                .fechaEmision(req.fechaEmision())
                .neto(req.neto())
                .exento(req.exento())
                .iva(req.iva())
                .ivaRetenido(req.ivaRetenidoODefecto())
                .total(req.total())
                .observacion(req.observacion())
                .ivaUsoComun(req.esIvaUsoComun())
                .codIvaNoRec(req.codIvaNoRec())
                .build();

        compraRepository.save(compra);
        return toResponse(compra);
    }

    @Transactional(readOnly = true)
    public List<CompraResponse> listar(Long empresaId, YearMonth periodo) {
        empresaService.buscar(empresaId);
        return compraRepository.delPeriodo(empresaId, periodo).stream()
                .map(CompraService::toResponse)
                .toList();
    }

    @Transactional
    public void eliminar(Long empresaId, Long id) {
        DocumentoCompra compra = compraRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Compra", id));
        compraRepository.delete(compra);
    }

    /**
     * Reglas de negocio del registro: tipo admitido y coherencia aritmetica de
     * los montos (total = neto + exento + iva - ivaRetenido; la retencion del
     * comprador, tipica de la factura de compra 46, no puede exceder el IVA).
     * Package-private y estatico para poder testearlo como unidad pura.
     */
    static void validar(CompraRequest req) {
        if (!TIPOS_PERMITIDOS.contains(req.tipoDte())) {
            throw new ReglaNegocioException(
                    "Tipo de documento de compra no admitido: " + req.tipoDte()
                            + " (admitidos: 33, 34, 46, 56, 61)");
        }
        long retenido = req.ivaRetenidoODefecto();
        if (retenido > req.iva()) {
            throw new ReglaNegocioException(
                    "El IVA retenido (" + retenido + ") no puede exceder el IVA del documento (" + req.iva() + ")");
        }
        if (req.codIvaNoRec() != null && !CODIGOS_IVA_NO_REC.contains(req.codIvaNoRec())) {
            throw new ReglaNegocioException(
                    "Codigo de IVA no recuperable desconocido: " + req.codIvaNoRec()
                            + " (admitidos: 1, 2, 3, 4, 9)");
        }
        // Un mismo IVA no puede ser a la vez de uso comun, no recuperable o retenido:
        // son destinos excluyentes del credito.
        int destinos = (req.esIvaUsoComun() ? 1 : 0) + (req.codIvaNoRec() != null ? 1 : 0)
                + (retenido > 0 ? 1 : 0);
        if (destinos > 1) {
            throw new ReglaNegocioException(
                    "IVA uso comun, IVA no recuperable e IVA retenido son excluyentes entre si");
        }
        if ((req.esIvaUsoComun() || req.codIvaNoRec() != null) && req.iva() <= 0) {
            throw new ReglaNegocioException(
                    "IVA uso comun o no recuperable requieren un monto de IVA mayor que cero");
        }
        long esperado = req.neto() + req.exento() + req.iva() - retenido;
        if (req.total() != esperado) {
            throw new ReglaNegocioException(
                    "El total (" + req.total() + ") no coincide con neto + exento + IVA - IVA retenido ("
                            + esperado + ")");
        }
    }

    static CompraResponse toResponse(DocumentoCompra c) {
        return new CompraResponse(
                c.getId(), c.getTipoDte(), c.getFolio(), c.getRutProveedor(), c.getRazonSocial(),
                c.getFechaEmision(), c.getNeto(), c.getExento(), c.getIva(), c.getIvaRetenido(),
                c.getTotal(), c.getObservacion(), c.isIvaUsoComun(), c.getCodIvaNoRec(), c.getCreadoEn());
    }
}
