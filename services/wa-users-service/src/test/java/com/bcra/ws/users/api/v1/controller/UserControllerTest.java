package com.bcra.ws.users.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.ws.users.exception.DuplicateDocumentException;
import com.bcra.ws.users.exception.GlobalExceptionHandler;
import com.bcra.ws.users.exception.InvalidStatusTransitionException;
import com.bcra.ws.users.exception.UserNotFoundException;
import com.bcra.ws.users.api.v1.dto.UserAuditEventResponse;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Web layer tests for {@link UserController} (validation + error mapping).
 */
@WebMvcTest(controllers = UserController.class,
        properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
@Import(GlobalExceptionHandler.class)
@ExtendWith(OutputCaptureExtension.class)
class UserControllerTest {

    @Test
    @DisplayName("Every domain and fallback error exposes exactly the public contract and an ISO timestamp")
    void shouldExposeOnlyPublicErrorFields() throws Exception {
        List<RuntimeException> failures = List.of(
                new UserNotFoundException("missing"),
                new DuplicateDocumentException(DocumentType.DNI, "12345678"),
                new com.bcra.ws.users.exception.DuplicateEmailException(),
                new InvalidStatusTransitionException(UserStatus.ACTIVE, UserStatus.ACTIVE),
                new IllegalArgumentException("private data"),
                new RuntimeException("private data"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (RuntimeException failure : failures) {
            org.mockito.Mockito.doThrow(failure).when(userService).findById("contract");
            String body = mockMvc.perform(get("/api/v1/users/contract"))
                    .andReturn().getResponse().getContentAsString();
            var error = mapper.readTree(body);
            List<String> fields = new java.util.ArrayList<>();
            error.fieldNames().forEachRemaining(fields::add);

            assertThat(fields).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
            assertThat(error.get("details").isArray()).isTrue();
            assertThat(error.get("message").asText()).isNotBlank();
            assertThat(Instant.parse(error.get("timestamp").asText())).isNotNull();
            for (var detail : error.get("details")) {
                assertThat(detail.size()).isEqualTo(2);
                assertThat(detail.get("field").asText()).isNotBlank();
                assertThat(detail.get("message").asText()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("Sensitive exception messages and documents are absent from error responses and logs")
    void shouldKeepSensitiveValuesOutOfErrorsAndLogs(CapturedOutput output) throws Exception {
        String privateData = "12345678 private@email.com +5491112345678";
        when(userService.findById("failure")).thenThrow(new RuntimeException(privateData));
        String response = mockMvc.perform(get("/api/v1/users/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        when(userService.findByDocument(DocumentType.DNI, "12345678"))
                .thenThrow(UserNotFoundException.byDocument("DNI", "12345678"));
        response += mockMvc.perform(get("/api/v1/users?documentType=DNI&documentNumber=12345678"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        when(userService.findById("duplicate"))
                .thenThrow(new DuplicateDocumentException(DocumentType.DNI, "12345678"));
        response += mockMvc.perform(get("/api/v1/users/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].message").value("Document already registered"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("12345678", "private@email.com", "+5491112345678",
                "RuntimeException", "stackTrace");
        assertThat(output.getAll()).doesNotContain("12345678", "private@email.com", "+5491112345678");
    }

    @Test
    @DisplayName("Constraint violations use VALIDATION_ERROR with field details")
    void shouldHandleConstraintViolation() throws Exception {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new RequiredValue(""));
            when(userService.findById("invalid"))
                    .thenThrow(new jakarta.validation.ConstraintViolationException(violations));
            mockMvc.perform(get("/api/v1/users/invalid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("value"))
                    .andExpect(jsonPath("$.details[0].message").value("Invalid value"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    private record RequiredValue(@jakarta.validation.constraints.NotBlank String value) {
    }

    @Test
    @DisplayName("Missing and invalid query parameters use BAD_REQUEST")
    void shouldRejectInvalidParameters() throws Exception {
        for (String query : List.of("", "?documentType=DNI", "?documentNumber=123",
                "?documentType=INVALID&documentNumber=123")) {
            mockMvc.perform(get("/api/v1/users" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    @Test
    @DisplayName("Malformed JSON, missing body and invalid enum use BAD_REQUEST")
    void shouldRejectInvalidJson() throws Exception {
        for (String body : List.of("{", "", "{\"status\":\"INVALID\"}")) {
            mockMvc.perform(patch("/api/v1/users/user-1/status")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.details").isEmpty())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Test
    @DisplayName("Unknown body field uses VALIDATION_ERROR")
    void shouldRejectUnknownField() throws Exception {
        mockMvc.perform(patch("/api/v1/users/user-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"secret\":\"private\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("body"))
                .andExpect(jsonPath("$.details[0].message").value("Unknown field"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Illegal arguments return safe BAD_REQUEST messages")
    void shouldHideIllegalArgumentMessage() throws Exception {
        when(userService.findById("bad")).thenThrow(new IllegalArgumentException("private@email.com"));
        mockMvc.perform(get("/api/v1/users/bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameters"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Email conflict has typed email detail")
    void shouldHandleEmailConflict() throws Exception {
        when(userService.update(eq("user-1"), any(), isNull()))
                .thenThrow(new com.bcra.ws.users.exception.DuplicateEmailException());
        mockMvc.perform(patch("/api/v1/users/user-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "firstName", "lastName",
            "email", "phone", "address", "birthDate"})
    @DisplayName("Should reject registration when any mandatory field is absent")
    void shouldRejectMissingMandatoryField(String field) throws Exception {
        ObjectNode body = completeRegistration();
        body.remove(field);
        assertInvalidRegistration(body, field);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationFields")
    @DisplayName("Should reject null, blank, malformed and non-past registration data")
    void shouldRejectInvalidMandatoryField(String field, String value) throws Exception {
        ObjectNode body = completeRegistration();
        body.put(field, value);
        assertInvalidRegistration(body, field);
    }

    private static Stream<Arguments> invalidRegistrationFields() {
        return Stream.of(
                Arguments.of("documentType", (String) null),
                Arguments.of("documentNumber", " "),
                Arguments.of("firstName", " "),
                Arguments.of("lastName", " "),
                Arguments.of("email", "invalid"),
                Arguments.of("phone", (String) null),
                Arguments.of("phone", ""),
                Arguments.of("phone", " "),
                Arguments.of("address", (String) null),
                Arguments.of("address", ""),
                Arguments.of("address", " "),
                Arguments.of("birthDate", (String) null),
                Arguments.of("birthDate", LocalDate.now().toString()),
                Arguments.of("birthDate", LocalDate.now().plusDays(1).toString()));
    }

    private ObjectNode completeRegistration() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {
                  "documentType": "DNI",
                  "documentNumber": "30111222",
                  "firstName": "Maria",
                  "lastName": "Lopez",
                  "email": "maria.lopez@example.com",
                  "phone": "+54911111111",
                  "address": "Av. Corrientes 1234",
                  "birthDate": "1990-05-20"
                }
                """);
    }

    private void assertInvalidRegistration(ObjectNode body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Should return 201 when create is valid")
    void shouldReturnCreated_whenCreateValid() throws Exception {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        when(userService.create(any(), isNull())).thenReturn(UserResponse.builder()
                .id("user-1")
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .phone("+54911111111")
                .address("Av. Corrientes 1234")
                .birthDate(LocalDate.of(1990, 5, 20))
                .status(UserStatus.ACTIVE)
                .channelLinkEligible(true)
                .preferredLanguage("es")
                .createdAt(now)
                .updatedAt(now)
                .build());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "DNI",
                                  "documentNumber": "30111222",
                                  "firstName": "Maria",
                                  "lastName": "Lopez",
                                  "email": "maria.lopez@example.com",
                                  "phone": "+54911111111",
                                  "address": "Av. Corrientes 1234",
                                  "birthDate": "1990-05-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.address").value("Av. Corrientes 1234"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andExpect(jsonPath("$.preferredLanguage").value("es"))
                .andExpect(jsonPath("$.createdAt").value(now.toString()))
                .andExpect(jsonPath("$.updatedAt").value(now.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.channelLinkEligible").value(true));
    }

    @Test
    @DisplayName("Should return 400 VALIDATION_ERROR when create body invalid")
    void shouldReturnValidationError_whenCreateInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "DNI",
                                  "documentNumber": "",
                                  "firstName": "Maria",
                                  "lastName": "Lopez",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("Should return 409 when document is duplicate")
    void shouldReturnConflict_whenDuplicateDocument() throws Exception {
        when(userService.create(any(), any()))
                .thenThrow(new DuplicateDocumentException(DocumentType.DNI, "30111222"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "ops")
                        .content("""
                                {
                                  "documentType": "DNI",
                                  "documentNumber": "30111222",
                                  "firstName": "Maria",
                                  "lastName": "Lopez",
                                  "email": "maria.lopez@example.com",
                                  "phone": "+54911111111",
                                  "address": "Av. Corrientes 1234",
                                  "birthDate": "1990-05-20"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("documentNumber"));
    }

    @Test
    @DisplayName("Should return 404 when user id not found")
    void shouldReturnNotFound_whenUserMissing() throws Exception {
        when(userService.findById("missing")).thenThrow(new UserNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/users/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Should return 200 when finding by document")
    void shouldReturnOk_whenFindByDocument() throws Exception {
        when(userService.findByDocument(DocumentType.DNI, "30111222"))
                .thenReturn(UserResponse.builder()
                        .id("user-1")
                        .documentType(DocumentType.DNI)
                        .documentNumber("30111222")
                        .status(UserStatus.ACTIVE)
                        .channelLinkEligible(true)
                        .build());

        mockMvc.perform(get("/api/v1/users")
                        .param("documentType", "DNI")
                        .param("documentNumber", "30111222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"));
    }

    @Test
    @DisplayName("Should return 400 when update has invalid email")
    void shouldReturnValidationError_whenUpdateEmailInvalid() throws Exception {
        mockMvc.perform(patch("/api/v1/users/user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should return 409 when status transition invalid")
    void shouldReturnConflict_whenInvalidTransition() throws Exception {
        when(userService.changeStatus(eq("user-1"), any(), isNull()))
                .thenThrow(new InvalidStatusTransitionException(UserStatus.ACTIVE, UserStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/users/user-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Should return audit trail")
    void shouldReturnAuditTrail_whenRequested() throws Exception {
        when(userService.findAuditTrail("user-1")).thenReturn(List.of(
                UserAuditEventResponse.builder()
                        .id("a1")
                        .userId("user-1")
                        .action(AuditAction.USER_CREATED)
                        .result(AuditResult.SUCCESS)
                        .timestamp(Instant.parse("2026-10-19T12:00:00Z"))
                        .build()));

        mockMvc.perform(get("/api/v1/users/user-1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("USER_CREATED"));
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_ERROR for unexpected failures")
    void shouldReturnInternalError_whenUnexpected() throws Exception {
        when(userService.findById("user-1")).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/users/user-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
