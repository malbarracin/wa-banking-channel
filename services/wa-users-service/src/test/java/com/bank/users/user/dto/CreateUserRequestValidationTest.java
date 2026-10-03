package com.bank.users.user.dto;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bank.users.user.entity.DocumentType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreateUserRequest — validación")
class CreateUserRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Solicitud completa con opcionales vacíos es válida")
    void validRequest() {
        var request = new CreateUserRequest(DocumentType.PASAPORTE, "AB123", "Ana", "Gómez",
                null, null, LocalDate.of(1985, 1, 1));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Obligatorios ausentes o en blanco y email inválido son rechazados")
    void invalidRequest() {
        var request = new CreateUserRequest(null, " ", "", "  ", null, "no-es-email", null);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("documentType", "documentNumber", "firstName", "lastName",
                        "email", "birthDate");
    }
}
