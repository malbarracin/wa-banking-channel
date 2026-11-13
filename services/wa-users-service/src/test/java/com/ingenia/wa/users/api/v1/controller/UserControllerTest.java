package com.ingenia.wa.users.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ingenia.wa.users.api.error.DuplicateDocumentException;
import com.ingenia.wa.users.api.error.GlobalExceptionHandler;
import com.ingenia.wa.users.api.error.InvalidStatusTransitionException;
import com.ingenia.wa.users.api.error.UserNotFoundException;
import com.ingenia.wa.users.api.v1.dto.UserAuditEventResponse;
import com.ingenia.wa.users.api.v1.dto.UserResponse;
import com.ingenia.wa.users.domain.AuditAction;
import com.ingenia.wa.users.domain.AuditResult;
import com.ingenia.wa.users.domain.DocumentType;
import com.ingenia.wa.users.domain.UserStatus;
import com.ingenia.wa.users.service.UserService;

/**
 * Web layer tests for {@link UserController} (validation + error mapping).
 */
@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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
                                  "email": "maria.lopez@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
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
                                  "email": "maria.lopez@example.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
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
    @DisplayName("Should return 400 when status transition invalid")
    void shouldReturnBadRequest_whenInvalidTransition() throws Exception {
        when(userService.changeStatus(eq("user-1"), any(), isNull()))
                .thenThrow(new InvalidStatusTransitionException(UserStatus.ACTIVE, UserStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/users/user-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
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
