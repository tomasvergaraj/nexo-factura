package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.common.validation.RutValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class EmpresaDtos {

    private EmpresaDtos() {}

    public record EmpresaRequest(
            @NotBlank @RutValido String rut,
            @NotBlank String razonSocial,
            @NotBlank String giro,
            Integer actividadEconomica,
            @NotBlank String direccion,
            @NotBlank String comuna,
            String ciudad,
            String telefono,
            @Email String email
    ) {}

    public record EmpresaResponse(
            Long id,
            String rut,
            String razonSocial,
            String giro,
            Integer actividadEconomica,
            String direccion,
            String comuna,
            String ciudad,
            String telefono,
            String email
    ) {}
}
