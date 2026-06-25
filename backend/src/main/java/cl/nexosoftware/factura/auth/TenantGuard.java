package cl.nexosoftware.factura.auth;

import org.springframework.stereotype.Component;

/**
 * Unico guard de aislamiento multi-tenant del sistema. Se invoca como
 * {@code @PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")}.
 *
 * <p>Regla: el {@code empresaId} del path y el claim {@code empresaId} del JWT
 * deben ser ambos no-null e iguales; cualquier otro caso deniega el acceso.
 */
@Component("tenantGuard")
public class TenantGuard {

    public boolean checkEmpresa(Long empresaId) {
        Long claim = SecurityUtils.currentEmpresaId();
        return empresaId != null && claim != null && empresaId.equals(claim);
    }
}
