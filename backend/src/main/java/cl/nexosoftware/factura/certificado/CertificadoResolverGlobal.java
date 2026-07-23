package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Modo GLOBAL: el PKCS#12 unico del ambiente (APP_SII_CERT_PATH), cargado y
 * validado FAIL-FAST al construirse (patron JwtSecretValidator): si el archivo
 * no existe, la clave es incorrecta o el certificado esta vencido, el contexto
 * no levanta — mejor que descubrirlo al emitir el primer documento.
 *
 * Toda empresa firma con este certificado: el comportamiento historico de la
 * app y el del ambiente de certificacion (docker-compose.cert.yml).
 */
@Slf4j
public class CertificadoResolverGlobal implements CertificadoResolver {

    private final CertificadoFirma certificado;

    private CertificadoResolverGlobal(CertificadoFirma certificado) {
        this.certificado = certificado;
    }

    public static CertificadoResolverGlobal cargar(AppProperties props) {
        String path = props.sii().certificadoPath();
        String password = props.sii().certificadoPassword();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "El modo de firma GLOBAL requiere el certificado digital del ambiente: "
                            + "configure APP_SII_CERT_PATH (o cambie a APP_SII_FIRMA_MODO=POR_EMPRESA)");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "El modo de firma GLOBAL requiere la clave del certificado: configure APP_SII_CERT_PASSWORD");
        }
        CertificadoFirma cert = CertificadoFirma.desdeArchivo(path, password, props.sii().rutFirmante());
        log.info("Certificado digital GLOBAL cargado: subject='{}', vigente hasta {}, RUT firmante {}",
                cert.subject(), cert.certificado().getNotAfter(), cert.rutFirmante());
        return new CertificadoResolverGlobal(cert);
    }

    @Override
    public CertificadoFirma paraEmpresa(Long empresaId) {
        return certificado;
    }

    @Override
    public Optional<CertificadoFirma> paraEmpresaSiExiste(Long empresaId) {
        return Optional.of(certificado);
    }
}
