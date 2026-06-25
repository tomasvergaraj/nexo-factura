package cl.nexosoftware.factura.cliente;

import cl.nexosoftware.factura.cliente.ClienteDtos.*;
import cl.nexosoftware.factura.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas/{empresaId}/clientes")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Clientes", description = "Receptores de documentos")
public class ClienteController {

    private final ClienteService service;

    @GetMapping
    public PageResponse<ClienteResponse> listar(@PathVariable Long empresaId,
                                                @RequestParam(required = false) String q,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(empresaId, q, pageable);
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@PathVariable Long empresaId,
                                                 @Valid @RequestBody ClienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(empresaId, req));
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long empresaId, @PathVariable Long id,
                                      @Valid @RequestBody ClienteRequest req) {
        return service.actualizar(empresaId, id, req);
    }
}
