package com.bank.users.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
@DisplayName("Contratos JSON y validación de usuario")
class UserJsonContractTest {

    private static final String VALID_JSON = """
            {"documentType":"DNI","documentNumber":"AB-123","firstName":"Ana","lastName":"Gomez",
             "birthDate":"1992-02-29"}
            """;

    private static ValidatorFactory factory;
    private static Validator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @CsvSource({"documentType,false", "documentNumber,false", "firstName,false", "lastName,false",
            "birthDate,false", "documentType,true", "documentNumber,true", "firstName,true",
            "lastName,true", "birthDate,true"})
    @DisplayName("Cada obligatorio ausente o null falla independientemente")
    void requiredField(String field, boolean explicitNull) throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        if (explicitNull) {
            json.putNull(field);
        } else {
            json.remove(field);
        }

        CreateUserRequest request = objectMapper.treeToValue(json, CreateUserRequest.class);

        assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString).containsExactly(field);
    }

    @ParameterizedTest
    @CsvSource({"documentNumber,''", "documentNumber,'  '", "firstName,''", "firstName,'  '",
            "lastName,''", "lastName,'  '"})
    @DisplayName("Cada texto obligatorio rechaza vacío y espacios")
    void blankRequiredField(String field, String value) throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        json.put(field, value);

        CreateUserRequest request = objectMapper.treeToValue(json, CreateUserRequest.class);

        assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString).containsExactly(field);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ana@example.com"})
    @DisplayName("Fecha ISO válida y email opcional son aceptados sin normalizar documento")
    void validRequest(String email) throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        json.put("email", email);

        CreateUserRequest request = objectMapper.treeToValue(json, CreateUserRequest.class);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.birthDate()).isEqualTo(LocalDate.of(1992, 2, 29));
        assertThat(request.documentNumber()).isEqualTo("AB-123");
        assertThat(request.displayName()).isNull();
        assertThat(request.email()).isEqualTo(email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "ana@", "@example.com"})
    @DisplayName("Email informado con formato inválido es rechazado")
    void invalidEmail(String email) throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        json.put("email", email);

        CreateUserRequest request = objectMapper.treeToValue(json, CreateUserRequest.class);

        assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString).containsExactly("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1992-02-30", "29/02/1992", "not-a-date"})
    @DisplayName("Fecha imposible o formato no ISO falla al deserializar")
    void invalidBirthDate(String birthDate) throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        json.put("birthDate", birthDate);

        assertThatThrownBy(() -> objectMapper.treeToValue(json, CreateUserRequest.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    @DisplayName("Los enums admiten exactamente los valores del contrato")
    void exactEnums() {
        assertThat(DocumentType.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("DNI", "PASAPORTE", "CUIT", "CUIL");
        assertThat(UserStatus.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("ACTIVE", "SUSPENDED", "INACTIVE");
    }

    @Test
    @DisplayName("Un tipo documental desconocido falla al deserializar")
    void unknownDocumentType() throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.readTree(VALID_JSON);
        json.put("documentType", "OTRO");

        assertThatThrownBy(() -> objectMapper.treeToValue(json, CreateUserRequest.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    @DisplayName("La proyección JSON contiene exactamente los once campos públicos")
    void exactResponseFields() throws Exception {
        Instant createdAt = Instant.parse("2026-10-03T19:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-03T20:00:00Z");
        UserResponse response = new UserResponse(7L, DocumentType.CUIL, "123", "Ana", "Gomez", "Ani",
                "ana@example.com", LocalDate.of(1992, 2, 29), UserStatus.SUSPENDED, createdAt, updatedAt);

        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactlyInAnyOrder("id", "documentType", "documentNumber", "firstName",
                "lastName", "displayName", "email", "birthDate", "status", "createdAt", "updatedAt");
        assertThat(json.get("birthDate").asText()).isEqualTo("1992-02-29");
        assertThat(json.get("createdAt").asText()).isEqualTo(createdAt.toString());
        assertThat(json.get("updatedAt").asText()).isEqualTo(updatedAt.toString());
        assertThat(objectMapper.treeToValue(json, UserResponse.class)).isEqualTo(response);
    }
}
