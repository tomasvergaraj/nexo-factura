package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.certificado.CertificadoDtos.CertificadoResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Certificado digital de firma electronica por empresa (modo POR_EMPRESA). El
 * upload y la baja exigen ADMIN (material sensible); la metadata la puede ver
 * cualquier usuario de la empresa. El PKCS#12 y su clave jamas se devuelven:
 * las respuestas son solo metadata (vigencia, firmante, huella).
 */
@RestController
@RequestMapping("/api/empresas/{empresaId}/certificado")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Certificado digital", description = "PKCS#12 de firma electronica por empresa")
public class CertificadoController {

    private final CertificadoEmpresaService service;

    /** Metadata del certificado activo; 404 si la empresa no tiene ninguno. */
    @GetMapping
    public CertificadoResponse obtener(@PathVariable Long empresaId) {
        return service.activo(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa " + empresaId + " no tiene certificado digital cargado"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasRole('ADMIN')")
    public ResponseEntity<CertificadoResponse> subir(
            @PathVariable Long empresaId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("password") String password,
            @RequestParam(value = "rutFirmante", required = false) String rutFirmante) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Debe adjuntar el archivo del certificado (.p12/.pfx)");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo del certificado: " + e.getMessage());
        }
        CertificadoResponse res = service.subir(
                empresaId, archivo.getOriginalFilename(), bytes, password, rutFirmante);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @DeleteMapping
    @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId) and hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long empresaId) {
        service.eliminar(empresaId);
    }
}
