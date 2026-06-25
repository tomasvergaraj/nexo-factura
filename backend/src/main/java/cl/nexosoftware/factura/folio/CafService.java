package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.folio.CafDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CafService {

    private final CafRepository repository;

    @Transactional(readOnly = true)
    public List<CafResponse> listar(Long empresaId) {
        return repository.findByEmpresaIdOrderByTipoDteAscFolioDesdeAsc(empresaId)
                .stream().map(CafResponse::de).toList();
    }

    @Transactional
    public CafResponse cargar(Long empresaId, CafRequest req) {
        if (req.folioHasta() < req.folioDesde()) {
            throw new ReglaNegocioException("El folio hasta debe ser mayor o igual al folio desde");
        }
        Caf caf = Caf.builder()
                .empresaId(empresaId)
                .tipoDte(req.tipoDte())
                .folioDesde(req.folioDesde())
                .folioHasta(req.folioHasta())
                .folioActual(req.folioDesde() - 1) // aun no se emite ningun folio
                .xmlCaf(req.xmlCaf())
                .fechaAutorizacion(req.fechaAutorizacion())
                .fechaVencimiento(req.fechaVencimiento())
                .agotado(false)
                .build();
        return CafResponse.de(repository.save(caf));
    }
}
