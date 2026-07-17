package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/empresas/{empresaId}/libros")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Libros IECV", description = "Libros de compra y venta del periodo tributario")
public class LibroController {

    private final LibroService service;

    @GetMapping("/{tipo}")
    @Operation(summary = "Libro del periodo (YYYY-MM; por defecto el mes actual). tipo: ventas | compras. "
            + "Ventas: boletas resumidas, anulados marcados sin sumar, rechazados excluidos. "
            + "Compras: documentos recibidos registrados.")
    public LibroResponse libro(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return service.libro(empresaId, operacion(tipo), normalizar(periodo));
    }

    @GetMapping(value = "/{tipo}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Libro del periodo como XML LibroCompraVenta (sin firmar ni enviar al SII).")
    public ResponseEntity<String> libroXml(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(service.libroXml(empresaId, operacion(tipo), normalizar(periodo)));
    }

    private static TipoOperacion operacion(String tipo) {
        return switch (tipo) {
            case "ventas" -> TipoOperacion.VENTA;
            case "compras" -> TipoOperacion.COMPRA;
            default -> throw RecursoNoEncontradoException.de("Libro", tipo);
        };
    }

    private static YearMonth normalizar(YearMonth periodo) {
        return periodo != null ? periodo : YearMonth.now();
    }
}
