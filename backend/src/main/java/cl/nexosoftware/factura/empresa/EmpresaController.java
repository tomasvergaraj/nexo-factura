package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.empresa.EmpresaDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
@Tag(name = "Empresas", description = "Datos del emisor (contribuyente)")
public class EmpresaController {

    private final EmpresaService service;

    @GetMapping
    public List<EmpresaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public EmpresaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> crear(@Valid @RequestBody EmpresaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public EmpresaResponse actualizar(@PathVariable Long id, @Valid @RequestBody EmpresaRequest req) {
        return service.actualizar(id, req);
    }
}
