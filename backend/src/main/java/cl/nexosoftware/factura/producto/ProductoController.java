package cl.nexosoftware.factura.producto;

import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.producto.ProductoDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas/{empresaId}/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catalogo de items")
public class ProductoController {

    private final ProductoService service;

    @GetMapping
    public PageResponse<ProductoResponse> listar(@PathVariable Long empresaId,
                                                 @RequestParam(required = false) String q,
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(empresaId, q, pageable);
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@PathVariable Long empresaId,
                                                  @Valid @RequestBody ProductoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(empresaId, req));
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long empresaId, @PathVariable Long id,
                                       @Valid @RequestBody ProductoRequest req) {
        return service.actualizar(id, req);
    }
}
