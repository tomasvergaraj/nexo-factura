package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.folio.CafDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas/{empresaId}/folios")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Folios (CAF)", description = "Codigos de Autorizacion de Folios del SII")
public class CafController {

    private final CafService service;

    @GetMapping
    public List<CafResponse> listar(@PathVariable Long empresaId) {
        return service.listar(empresaId);
    }

    @PostMapping
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public ResponseEntity<CafResponse> cargar(@PathVariable Long empresaId,
                                              @Valid @RequestBody CafRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cargar(empresaId, req));
    }
}
