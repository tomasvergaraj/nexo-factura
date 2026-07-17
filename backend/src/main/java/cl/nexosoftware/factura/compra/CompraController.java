package cl.nexosoftware.factura.compra;

import cl.nexosoftware.factura.compra.CompraDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/empresas/{empresaId}/compras")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Compras", description = "Registro de documentos recibidos para el libro de compras")
public class CompraController {

    private final CompraService service;

    @GetMapping
    @Operation(summary = "Compras registradas de un periodo (YYYY-MM). Si se omite, el mes actual.")
    public List<CompraResponse> listar(
            @PathVariable Long empresaId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return service.listar(empresaId, periodo != null ? periodo : YearMonth.now());
    }

    @PostMapping
    @Operation(summary = "Registrar un documento recibido (33/34/46/56/61). "
            + "Exige total = neto + exento + IVA; duplicado (tipo+folio+proveedor) responde 409.")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public ResponseEntity<CompraResponse> crear(@PathVariable Long empresaId,
                                                @Valid @RequestBody CompraRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(empresaId, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un documento de compra registrado")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long empresaId, @PathVariable Long id) {
        service.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
