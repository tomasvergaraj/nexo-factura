package cl.nexosoftware.factura.tributario;

import java.util.Optional;

/**
 * Resuelve la identidad de firma de una empresa segun el modo de firma
 * ({@link FirmaModo}):
 *
 * <ul>
 *   <li><b>GLOBAL</b> — el PKCS#12 unico del ambiente (APP_SII_CERT_PATH),
 *       cargado fail-fast en el arranque y devuelto para toda empresa: el
 *       comportamiento historico y el del E2E de certificacion;</li>
 *   <li><b>POR_EMPRESA</b> — el certificado cifrado en BD de esa empresa;
 *       sin certificado configurado el error es de negocio y explicito
 *       (jamas un fallback silencioso al certificado de otro tenant);</li>
 *   <li><b>dev/test</b> (perfil != prod) — no hay certificado: los flujos que
 *       necesitan el RUN del firmante caen al RUT del emisor via
 *       {@link #paraEmpresaSiExiste}.</li>
 * </ul>
 */
public interface CertificadoResolver {

    /**
     * Certificado de firma de la empresa. Lanza
     * {@link cl.nexosoftware.factura.common.exception.ReglaNegocioException}
     * si la empresa no tiene certificado configurado o esta vencido.
     */
    CertificadoFirma paraEmpresa(Long empresaId);

    /** Variante opcional para los flujos con fallback (libro, intercambio, PDF). */
    Optional<CertificadoFirma> paraEmpresaSiExiste(Long empresaId);
}
