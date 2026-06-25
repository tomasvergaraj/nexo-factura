package cl.nexosoftware.factura.cliente;

import cl.nexosoftware.factura.common.validation.RutValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class ClienteDtos {

    private ClienteDtos() {}

    public record ClienteRequest(
            @NotBlank @RutValido String rut,
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
