package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.auth.SecurityUtils;
import cl.nexosoftware.factura.certificado.CertificadoDtos.CertificadoResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.seguridad.CifradorSecretos;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Alta, consulta y baja del certificado digital de una empresa (modo
 * POR_EMPRESA). El PKCS#12 se valida EN MEMORIA al subirlo (clave correcta,
 * clave privada presente, vigencia, RUN del firmante) y se guarda CIFRADO
 * ({@link CifradorSecretos}); la clave y el material jamas vuelven a salir.
 *
 * Historial 1-N con un unico activo: subir uno nuevo desactiva el anterior en
 * la misma transaccion (el indice parcial ux_certificado_empresa_activo lo
 * garantiza a nivel de BD).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CertificadoEmpresaService {

    private final CertificadoEmpresaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final CifradorSecretos cifrador;

    @Transactional
    public CertificadoResponse subir(Long empresaId, String nombreArchivo, byte[] p12,
                                     String password, String rutFirmanteOverride) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", empresaId));
        if (!cifrador.disponible()) {
            throw new ReglaNegocioException(
                    "El servidor no tiene APP_MASTER_KEY configurada: no se pueden guardar certificados cifrados");
        }
        if (p12 == null || p12.length == 0) {
            throw new ReglaNegocioException("El archivo del certificado (.p12/.pfx) viene vacio");
        }
        if (password == null || password.isBlank()) {
            throw new ReglaNegocioException("La clave del certificado es obligatoria");
        }

        // Valida abriendo el PKCS#12 en memoria: clave correcta, clave privada
        // presente, vigencia y RUN del firmante. Traduce el fail-fast del value
        // object a un error de negocio (422) para la API.
        CertificadoFirma cert;
        try {
            cert = CertificadoFirma.desdeP12(p12, password, rutFirmanteOverride);
        } catch (IllegalStateException e) {
            throw new ReglaNegocioException("El certificado no es utilizable: " + e.getMessage());
        }

        // El RUN del firmante debe corresponder a una persona autorizada; si el
        // override no vino y no se pudo extraer, desdeP12 ya habria fallado.
        repository.findByEmpresaIdAndActivoTrue(empresaId).ifPresent(anterior -> {
            anterior.setActivo(false);
            repository.save(anterior);
            log.info("Certificado {} de la empresa {} desactivado (reemplazo)", anterior.getId(), empresaId);
        });

        var x509 = cert.certificado();
        CertificadoEmpresa fila = CertificadoEmpresa.builder()
                .empresaId(empresaId)
                .nombreArchivo(nombreArchivo != null ? nombreArchivo : "certificado.p12")
                .p12Cifrado(cifrador.cifrar(p12))
                .passwordCifrada(cifrador.cifrar(password.getBytes(StandardCharsets.UTF_8)))
                .rutFirmante(cert.rutFirmante())
                .subject(cert.subject())
                .validoDesde(aOffset(x509.getNotBefore()))
                .validoHasta(aOffset(x509.getNotAfter()))
                .huellaSha256(cert.huellaSha256())
                .keyVersion(cifrador.versionActual())
                .activo(true)
                .creadoPor(SecurityUtils.currentEmail())
                .build();
        fila = repository.save(fila);
        log.info("Certificado digital cargado para la empresa {}: firmante {}, vigente hasta {}",
                empresaId, cert.rutFirmante(), x509.getNotAfter());
        return aResponse(fila);
    }

    @Transactional(readOnly = true)
    public Optional<CertificadoResponse> activo(Long empresaId) {
        return repository.findByEmpresaIdAndActivoTrue(empresaId).map(CertificadoEmpresaService::aResponse);
    }

    @Transactional
    public void eliminar(Long empresaId) {
        CertificadoEmpresa fila = repository.findByEmpresaIdAndActivoTrue(empresaId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La empresa " + empresaId + " no tiene certificado activo que eliminar"));
        fila.setActivo(false);
        repository.save(fila);
        log.info("Certificado digital de la empresa {} desactivado", empresaId);
    }

    private static CertificadoResponse aResponse(CertificadoEmpresa c) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        boolean vigente = !ahora.isBefore(c.getValidoDesde()) && !ahora.isAfter(c.getValidoHasta());
        long dias = Duration.between(ahora, c.getValidoHasta()).toDays();
        return new CertificadoResponse(
                c.getId(), c.getNombreArchivo(), c.getRutFirmante(), c.getSubject(),
                c.getValidoDesde(), c.getValidoHasta(), c.getHuellaSha256(),
                vigente, dias, c.getCreadoEn(), c.getCreadoPor());
    }

    private static OffsetDateTime aOffset(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
