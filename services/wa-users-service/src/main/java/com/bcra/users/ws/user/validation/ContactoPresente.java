package com.bcra.users.ws.user.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Exige al menos un contacto no vacío en el alta. */
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = ContactoPresenteValidator.class)
public @interface ContactoPresente {
    String message() default "Debe informar email o teléfono";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
