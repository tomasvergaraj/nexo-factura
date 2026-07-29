package cl.nexosoftware.factura.rcof;

import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/empresas/{empresaId}/rcof")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "RCOF", description = "Reporte de Consumo de Folios de boletas")
public class RcofController {

    private final RcofService service;
    private final RcofFirmaService firmaService;

    @GetMapping
    @Operation(summary = "Reporte diario de consumo de folios de boletas (39/41). "
            + "Si se omite la fecha, usa el dia de hoy.")
    public RcofResponse reporte(
            @PathVariable Long empresaId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.generar(empresaId, fecha);
    }

    @GetMapping(value = "/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "RCOF como XML ConsumoFolios SIN firmar, para inspeccion. "
            + "Sin firma no cumple el esquema del SII: el presentable es /xml-firmado.")
    public ResponseEntity<String> xml(
            @PathVariable Long empresaId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(service.generarXml(empresaId, fecha));
    }

    /**
     * POST y no GET porque consume secuencia: cada archivo firmado queda
     * registrado con su SecEnvio, y rehacer el dia declara una correccion.
     */
    @PostMapping(value = "/xml-firmado", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "RCOF firmado (XMLDSig) y validado contra ConsumoFolio_v10. "
            + "NO se envia al SII: la Res. Ex. SII N°53/2022 elimino esa obligacion; el archivo "
            + "es el adjunto del set de pruebas de certificacion de boletas. secEnvio se calcula "
            + "solo (1 la primera vez del dia, +1 al rehacerlo) y admite override.")
    public ResponseEntity<String> xmlFirmado(
            @PathVariable Long empresaId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer secEnvio) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(firmaService.xmlFirmado(empresaId, fecha, secEnvio));
    }
}
