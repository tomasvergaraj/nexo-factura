package cl.nexosoftware.factura.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ClienteDtos {

    private ClienteDtos() {}

    public record ClienteRequest(
            @NotBlank @Pattern(regexp = "^\\d{1,8}-[\\dkK]$", message = "RUT invalido") String rut,
            @NotBlank String razonSocial,
            String giro,
            String direccion,
            String comuna,
            String ciudad,
            @Email String email
    ) {}

    public record ClienteResponse(
            Long id,
            String rut,
            String razonSocial,
            String giro,
            String direccion,
            String comuna,
            String ciudad,
            String email,
            boolean activo
    ) {}
}
