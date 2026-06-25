package cl.nexosoftware.factura.dashboard;

import cl.nexosoftware.factura.dashboard.DashboardDtos.ResumenDashboard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/empresas/{empresaId}/dashboard")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Dashboard", description = "Indicadores de emision")
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    public ResumenDashboard resumen(@PathVariable Long empresaId) {
        return service.resumen(empresaId);
    }
}
