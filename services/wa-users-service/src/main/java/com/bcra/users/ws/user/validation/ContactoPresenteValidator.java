package com.bcra.users.ws.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.bcra.users.ws.user.dto.CreateUserRequest;

/** Verifica presencia de contacto sin imponer reglas de formato. */
public class ContactoPresenteValidator implements ConstraintValidator<ContactoPresente, CreateUserRequest> {

    @Override
    public boolean isValid(CreateUserRequest solicitud, ConstraintValidatorContext contexto) {
        if (solicitud == null || tieneTexto(solicitud.email()) || tieneTexto(solicitud.telefono())) {
            return true;
        }
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                .addPropertyNode("email").addConstraintViolation();
        return false;
    }

    private boolean tieneTexto(String texto) {
        return texto != null && !texto.isBlank();
    }
}
