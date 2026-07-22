package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas/{empresaId}/documentos")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Documentos (DTE)", description = "Emision y gestion de documentos tributarios")
public class DocumentoController {

    private final DocumentoService service;

    @GetMapping
    @Operation(summary = "Listar documentos, opcionalmente filtrando por estado")
    public PageResponse<DocumentoResumen> listar(@PathVariable Long empresaId,
                                                 @RequestParam(required = false) EstadoDte estado,
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(empresaId, estado, pageable);
    }

    @GetMapping("/{id}")
    public DocumentoResponse obtener(@PathVariable Long empresaId, @PathVariable Long id) {
        return service.obtener(empresaId, id);
    }

    @PostMapping
    @Operation(summary = "Crear un documento en borrador. Las notas de credito/debito (56/61) "
            + "exigen al menos una referencia coherente al documento original.")
    public ResponseEntity<DocumentoResponse> crear(@PathVariable Long empresaId,
                                                   @Valid @RequestBody CrearDocumentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(empresaId, req));
    }

    @PostMapping("/{id}/emitir")
    @Operation(summary = "Emitir: asigna folio, genera timbre, XML y firma. "
            + "Una nota de credito con referencia ANULA_DOCUMENTO anula el original ACEPTADO en la misma transaccion.")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public DocumentoResponse emitir(@PathVariable Long empresaId, @PathVariable Long id) {
        return service.emitir(empresaId, id);
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Enviar el documento firmado al SII")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public DocumentoResponse enviar(@PathVariable Long empresaId, @PathVariable Long id) {
        return service.enviarSii(empresaId, id);
    }

    @PostMapping("/{id}/reenviar")
    @Operation(summary = "Reenviar al SII un documento EN_CONTINGENCIA o RECHAZADO. "
            + "Reenvia el mismo XML firmado; si el SII sigue caido, queda EN_CONTINGENCIA. "
            + "Un EN_CONTINGENCIA sin TrackID se reconcilia por folio antes de subir el sobre "
            + "(si el SII ya lo tiene, se adopta su estado en vez de duplicar el envio); "
            + "forzar=true salta esa reconciliacion.")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public DocumentoResponse reenviar(@PathVariable Long empresaId, @PathVariable Long id,
                                      @RequestParam(defaultValue = "false") boolean forzar) {
        return service.reenviarSii(empresaId, id, forzar);
    }

    @PostMapping("/reenviar-pendientes")
    @Operation(summary = "Reintentar el envio de todos los documentos EN_CONTINGENCIA de la empresa "
            + "(del mas antiguo al mas nuevo). Los que fallan quedan en contingencia con su error.")
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasAnyRole('ADMIN','EMISOR')")
    public ReenvioMasivoResponse reenviarPendientes(@PathVariable Long empresaId) {
        return service.reenviarPendientes(empresaId);
    }

    @PostMapping("/{id}/estado-sii")
    @Operation(summary = "Actualizar el estado del envio consultandolo al SII")
    public DocumentoResponse estadoSii(@PathVariable Long empresaId, @PathVariable Long id) {
        return service.consultarEstadoSii(empresaId, id);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar la representacion impresa en PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long empresaId, @PathVariable Long id) {
        byte[] pdf = service.generarPdf(empresaId, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"dte-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
