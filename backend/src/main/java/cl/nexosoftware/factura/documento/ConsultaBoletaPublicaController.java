package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.auth.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Consulta publica de boletas — SIN autenticacion ({@code /api/public/**} esta
 * en los PUBLIC_PATHS de SecurityConfig). Es el sitio de verificacion que el
 * Formato de Boletas Electronicas del SII exige al emisor, cuya URL va impresa
 * bajo el timbre. La proteccion contra enumeracion vive en el servicio:
 * coincidencia exacta de los cinco datos impresos o 404 uniforme, con rate
 * limit por IP.
 */
@RestController
@RequestMapping("/api/public/boletas")
@RequiredArgsConstructor
@Tag(name = "Consulta publica de boletas",
        description = "Verificacion de boletas por los clientes, sin autenticacion")
public class ConsultaBoletaPublicaController {

    private final ConsultaBoletaPublicaService service;
    private final RateLimiter rateLimiter;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Representacion impresa (PDF) de una boleta emitida. Requiere los cinco "
            + "datos impresos en el documento: RUT del emisor, tipo (39/41), folio, fecha de "
            + "emision y monto total. Disponible por 3 meses desde la emision.")
    public ResponseEntity<byte[]> pdf(
            @RequestParam String rutEmisor,
            @RequestParam int tipo,
            @RequestParam long folio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam long total,
            HttpServletRequest request) {
        byte[] pdf = service.pdf(rutEmisor, tipo, folio, fecha, total, rateLimiter.clientIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"boleta-" + tipo + "-" + folio + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
