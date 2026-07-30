package cl.nexosoftware.factura.libro;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

/**
 * Libro de Boletas Electronicas (LibroBOLETA_v10), separado de
 * {@link LibroController} —que sirve el IECV— porque es otro documento y otro
 * esquema. Ruta propia para que no compita con {@code /libros/{tipo}}.
 */
@RestController
@RequestMapping("/api/empresas/{empresaId}/libro-boletas")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Libro de boletas", description = "Libro de Boletas Electronicas (set de certificacion)")
public class LibroBoletaController {

    private final LibroBoletaService service;

    @GetMapping(value = "/xml-firmado", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Libro de boletas del periodo (YYYY-MM; por defecto el mes actual), firmado "
            + "y validado contra LibroBOLETA_v10. NO se envia al SII: es el adjunto del correo del "
            + "set de pruebas de boletas. folioNotificacion es el numero de atencion del SII y es "
            + "obligatorio — el esquema solo admite el libro ESPECIAL.")
    public ResponseEntity<String> xmlFirmado(
            @PathVariable Long empresaId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam long folioNotificacion) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(service.xmlFirmado(empresaId,
                        periodo != null ? periodo : YearMonth.now(), folioNotificacion));
    }
}
