package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.seguridad.CifradorSecretos;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modo POR_EMPRESA: cada empresa firma con SU certificado, cifrado en BD
 * ({@link CertificadoEmpresa}). Sin certificado configurado el error es de
 * negocio y explicito — jamas un fallback silencioso a otro certificado, que
 * produciria un rechazo confuso del SII (STATUS=1, firmante sin permiso).
 *
 * El PKCS#12 descifrado se parsea una sola vez por huella (el PBKDF2 del
 * PKCS#12 cuesta decenas de ms): la fila se relee de BD en cada resolucion
 * (verdad fresca: un certificado recien subido rige de inmediato) y el cache
 * en memoria solo evita re-parsear la MISMA huella. La vigencia se re-verifica
 * en cada acceso: un certificado que vencio estando cacheado deja de servirse.
 */
@Slf4j
public class CertificadoResolverPorEmpresa implements CertificadoResolver {

    private static final int MAX_CACHE = 500;

    private final CertificadoEmpresaRepository repository;
    private final CifradorSecretos cifrador;
    private final Map<String, CertificadoFirma> porHuella = new ConcurrentHashMap<>();

    public CertificadoResolverPorEmpresa(CertificadoEmpresaRepository repository,
                                         CifradorSecretos cifrador) {
        if (!cifrador.disponible()) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY es obligatoria con app.sii.firma-modo=POR_EMPRESA: sin ella "
                            + "no se pueden descifrar los certificados de las empresas");
        }
        if (cifrador.esClaveDeDesarrollo()) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY no puede ser la clave de desarrollo conocida con firma POR_EMPRESA");
        }
        this.repository = repository;
        this.cifrador = cifrador;
    }

    @Override
    public CertificadoFirma paraEmpresa(Long empresaId) {
        CertificadoEmpresa fila = repository.findByEmpresaIdAndActivoTrue(empresaId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La empresa " + empresaId + " no tiene certificado digital configurado: "
                                + "suba su PKCS#12 en POST /api/empresas/" + empresaId + "/certificado"));
        CertificadoFirma cert;
        try {
            cert = porHuella.computeIfAbsent(fila.getHuellaSha256(), h -> parsear(fila));
        } catch (IllegalStateException e) {
            throw new ReglaNegocioException(
                    "El certificado digital de la empresa " + empresaId + " no se pudo usar: " + e.getMessage());
        }
        try {
            cert.certificado().checkValidity();
        } catch (CertificateException e) {
            porHuella.remove(fila.getHuellaSha256());
            throw new ReglaNegocioException(
                    "El certificado digital de la empresa " + empresaId + " esta vencido ("
                            + cert.certificado().getNotAfter() + "): suba uno vigente");
        }
        return cert;
    }

    @Override
    public Optional<CertificadoFirma> paraEmpresaSiExiste(Long empresaId) {
        if (repository.findByEmpresaIdAndActivoTrue(empresaId).isEmpty()) {
            return Optional.empty();
        }
        // Existe: si esta corrupto/vencido el error debe verse, no degradarse.
        return Optional.of(paraEmpresa(empresaId));
    }

    private CertificadoFirma parsear(CertificadoEmpresa fila) {
        if (porHuella.size() >= MAX_CACHE) {
            porHuella.clear(); // tope defensivo; en la practica hay un cert por tenant
        }
        byte[] p12 = cifrador.descifrar(fila.getP12Cifrado());
        String password = new String(cifrador.descifrar(fila.getPasswordCifrada()), StandardCharsets.UTF_8);
        CertificadoFirma cert = CertificadoFirma.desdeP12(p12, password, fila.getRutFirmante());
        log.info("Certificado de la empresa {} cargado (huella {}…, vigente hasta {})",
                fila.getEmpresaId(), fila.getHuellaSha256().substring(0, 12),
                cert.certificado().getNotAfter());
        return cert;
    }
}
