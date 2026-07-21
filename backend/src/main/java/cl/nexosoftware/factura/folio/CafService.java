package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.common.validation.Rut;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.folio.CafDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CafService {

    // IDK conocidos: 100 = llave del SII del ambiente de certificacion, 300 = produccion.
    private static final int IDK_CERTIFICACION = 100;
    private static final int IDK_PRODUCCION = 300;

    private final CafRepository repository;
    private final CafParser cafParser;
    private final EmpresaService empresaService;

    @Transactional(readOnly = true)
    public List<CafResponse> listar(Long empresaId) {
        return repository.findByEmpresaIdOrderByTipoDteAscFolioDesdeAsc(empresaId)
                .stream().map(CafResponse::de).toList();
    }

    @Transactional
    public CafResponse cargar(Long empresaId, CafRequest req) {
        Empresa empresa = empresaService.buscar(empresaId);
        CafData data = cafParser.parsear(req.xmlCaf());

        TipoDte tipoDte;
        try {
            tipoDte = TipoDte.desdeCodigo(data.td());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(
                    "El CAF es para un tipo de documento no soportado (TD=" + data.td() + ")");
        }

        // El timbre lleva el RE del CAF como RUT del emisor: un CAF de otro
        // contribuyente produciria DTE invalidos ante el SII.
        if (!Rut.normalizar(data.re()).equals(Rut.normalizar(empresa.getRut()))) {
            throw new ReglaNegocioException(
                    "El CAF pertenece al RUT " + data.re() + ", no al de la empresa (" + empresa.getRut() + ")");
        }

        if (repository.existsByEmpresaIdAndTipoDteAndFolioDesdeAndFolioHasta(
                empresaId, tipoDte, data.folioDesde(), data.folioHasta())) {
            throw new ReglaNegocioException(
                    "Ya existe un CAF de " + tipoDte.getDescripcion() + " con el rango "
                            + data.folioDesde() + "-" + data.folioHasta());
        }

        if (data.idk() != IDK_CERTIFICACION && data.idk() != IDK_PRODUCCION) {
            log.warn("CAF con IDK desconocido ({}) para empresa {}: se acepta, pero verifique su origen",
                    data.idk(), empresaId);
        }

        // Res. Ex. 58/2017: solo los CAF de documentos con derecho a credito
        // fiscal (33/56/61 de nuestro catalogo) caducan, a los 6 meses de FA.
        LocalDate vencimiento = tipoDte.cafVence() ? data.fechaAutorizacion().plusMonths(6) : null;
        if (vencimiento != null && vencimiento.isBefore(LocalDate.now())) {
            throw new ReglaNegocioException(
                    "El CAF esta vencido desde el " + vencimiento
                            + " (autorizado el " + data.fechaAutorizacion() + "); el SII rechaza sus folios");
        }

        Caf caf = Caf.builder()
                .empresaId(empresaId)
                .tipoDte(tipoDte)
                .folioDesde(data.folioDesde())
                .folioHasta(data.folioHasta())
                .folioActual(data.folioDesde() - 1) // aun no se emite ningun folio
                .xmlCaf(req.xmlCaf())
                .fechaAutorizacion(data.fechaAutorizacion())
                .fechaVencimiento(vencimiento)
                .agotado(false)
                .build();
        return CafResponse.de(repository.save(caf));
    }
}
