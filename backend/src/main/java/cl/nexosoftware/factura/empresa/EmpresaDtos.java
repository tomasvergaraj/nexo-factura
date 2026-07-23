package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.common.validation.RutValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

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
            // Direccion Regional / Unidad del SII, rotulada bajo el recuadro del
            // tipo de documento en la representacion impresa (Manual 1.1.4).
            String unidadSii,
            String ciudad,
            String telefono,
            @Email String email,
            // Resolucion SII que autoriza a la empresa como emisor electronico
            // (FchResol/NroResol de la caratula). Ambos o ninguno; vacios =
            // fallback de entorno. NroResol es 0 en el ambiente de certificacion.
            LocalDate fchResol,
            @PositiveOrZero Integer nroResol
    ) {}

    public record EmpresaResponse(
            Long id,
            String rut,
            String razonSocial,
            String giro,
            Integer actividadEconomica,
            String direccion,
            String comuna,
            String unidadSii,
            String ciudad,
            String telefono,
            String email,
            LocalDate fchResol,
            Integer nroResol
    ) {}
}
