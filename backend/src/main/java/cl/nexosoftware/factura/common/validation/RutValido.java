package cl.nexosoftware.factura.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Valida el digito verificador de un RUT chileno (modulo 11). */
@Documented
@Constraint(validatedBy = RutValidoValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface RutValido {

    String message() default "RUT invalido: digito verificador incorrecto";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
