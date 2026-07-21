package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.common.validation.RutValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public final class EmpresaDtos {

    private EmpresaDtos() {}

    public record EmpresaRequest(
            @NotBlank @RutValido String rut,
            @NotBlank String razonSocial,
            @NotBlank String giro,
            // Acteco del XSD: xs:positiveInteger con totalDigits 6. Un 0/negativo
            // o un codigo de 7 digitos dejaria a la empresa inemitible (422).
            @Positive @Max(999999) Integer actividadEconomica,
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
