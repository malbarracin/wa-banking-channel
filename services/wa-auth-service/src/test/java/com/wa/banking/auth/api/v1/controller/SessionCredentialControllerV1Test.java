package com.wa.banking.auth.api.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.auth.api.error.GlobalExceptionHandler;
import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialResponse;
import com.wa.banking.auth.exception.CredentialNotFoundException;
import com.wa.banking.auth.exception.VerificationRequiredException;
import com.wa.banking.auth.service.SessionCredentialService;
import com.wa.banking.auth.support.SessionCredentialTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionCredentialControllerV1.class)
@Import(GlobalExceptionHandler.class)
class SessionCredentialControllerV1Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionCredentialService sessionCredentialService;

    @Test
    @DisplayName("Should return 201 when issuing credential")
    void shouldReturnCreated_whenIssueCredential() throws Exception {
        IssueCredentialResponse response = IssueCredentialResponse.builder()
                .credentialId(SessionCredentialTestFixtures.CREDENTIAL_ID)
                .token(SessionCredentialTestFixtures.TOKEN)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        when(sessionCredentialService.issue(any(IssueCredentialRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.issueRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialId").value(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .andExpect(jsonPath("$.token").value(SessionCredentialTestFixtures.TOKEN));
    }

    @Test
    @DisplayName("Should return 400 VALIDATION_ERROR when channelLinkId missing")
    void shouldReturnValidationError_whenChannelLinkIdMissing() throws Exception {
        IssueCredentialRequest request = IssueCredentialRequest.builder()
                .bankUserId(SessionCredentialTestFixtures.BANK_USER_ID)
                .build();

        mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST when verification required")
    void shouldReturnBadRequest_whenVerificationRequired() throws Exception {
        when(sessionCredentialService.issue(any(IssueCredentialRequest.class)))
                .thenThrow(new VerificationRequiredException());

        mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.issueRequestUnverified())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Should return 204 when revoking credential")
    void shouldReturnNoContent_whenRevokeCredential() throws Exception {
        doNothing().when(sessionCredentialService)
                .revoke(eq(SessionCredentialTestFixtures.CREDENTIAL_ID), any(), any());

        mockMvc.perform(delete("/api/v1/sessions/credentials/{id}", SessionCredentialTestFixtures.CREDENTIAL_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return 200 with valid true when credential is valid")
    void shouldReturnOkWithValidTrue_whenValidateSuccess() throws Exception {
        ValidateCredentialResponse response = ValidateCredentialResponse.builder()
                .valid(true)
                .bankUserId(SessionCredentialTestFixtures.BANK_USER_ID)
                .channelLinkId(SessionCredentialTestFixtures.CHANNEL_LINK_ID)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        when(sessionCredentialService.validate(any(ValidateCredentialRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/credentials/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.validateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.bankUserId").value(SessionCredentialTestFixtures.BANK_USER_ID));
    }

    @Test
    @DisplayName("Should return 200 with valid false when credential is invalid")
    void shouldReturnOkWithValidFalse_whenValidateFailure() throws Exception {
        ValidateCredentialResponse response = ValidateCredentialResponse.builder()
                .valid(false)
                .build();
        when(sessionCredentialService.validate(any(ValidateCredentialRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/credentials/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.validateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("Should return 400 VALIDATION_ERROR when validate request missing token")
    void shouldReturnValidationError_whenValidateTokenMissing() throws Exception {
        ValidateCredentialRequest request = ValidateCredentialRequest.builder()
                .credentialId(SessionCredentialTestFixtures.CREDENTIAL_ID)
                .build();

        mockMvc.perform(post("/api/v1/sessions/credentials/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when credential not found on status")
    void shouldReturnNotFound_whenCredentialMissing() throws Exception {
        when(sessionCredentialService.getStatus(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenThrow(new CredentialNotFoundException(SessionCredentialTestFixtures.CREDENTIAL_ID));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/sessions/credentials/{id}", SessionCredentialTestFixtures.CREDENTIAL_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
