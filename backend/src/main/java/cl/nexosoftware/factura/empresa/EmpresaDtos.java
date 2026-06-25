package cl.nexosoftware.factura.empresa;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class EmpresaDtos {

    private EmpresaDtos() {}

    private static final String RUT_REGEX = "^\\d{1,8}-[\\dkK]$";

    public record EmpresaRequest(
            @NotBlank @Pattern(regexp = RUT_REGEX, message = "RUT invalido, use formato 76543210-9") String rut,
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
