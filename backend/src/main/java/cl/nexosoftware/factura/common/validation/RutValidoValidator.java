package cl.nexosoftware.factura.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida un RUT contra {@link Rut#esValido(String)}.
 *
 * <p>Un valor {@code null} o en blanco se considera valido aqui: la presencia
 * la cubre {@code @NotBlank}, de modo que el mensaje de DV solo aparece cuando
 * hay realmente un RUT con digito verificador incorrecto.
 */
public class RutValidoValidator implements ConstraintValidator<RutValido, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Rut.esValido(value);
    }
}
