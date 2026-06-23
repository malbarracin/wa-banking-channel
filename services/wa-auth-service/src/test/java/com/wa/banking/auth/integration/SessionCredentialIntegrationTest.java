package com.wa.banking.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.auth.entity.AuditAction;
import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.repository.SessionAuditEntryRepository;
import com.wa.banking.auth.repository.SessionCredentialRepository;
import com.wa.banking.auth.support.SessionCredentialTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SessionCredentialIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionCredentialRepository credentialRepository;

    @Autowired
    private SessionAuditEntryRepository auditEntryRepository;

    @BeforeEach
    void cleanDatabase() {
        auditEntryRepository.deleteAll();
        credentialRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete issue → validate → revoke → validate false flow with audit persistence")
    void shouldCompleteCredentialLifecycle_whenValidFlow() throws Exception {
        MvcResult issueResult = mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.issueRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialId").exists())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode issueBody = objectMapper.readTree(issueResult.getResponse().getContentAsString());
        String credentialId = issueBody.get("credentialId").asText();
        String token = issueBody.get("token").asText();

        var validateRequest = com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest.builder()
                .credentialId(credentialId)
                .token(token)
                .build();

        mockMvc.perform(post("/api/v1/sessions/credentials/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.bankUserId").value(SessionCredentialTestFixtures.BANK_USER_ID));

        mockMvc.perform(delete("/api/v1/sessions/credentials/{id}", credentialId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/sessions/credentials/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        var storedCredential = credentialRepository.findById(credentialId);
        assertThat(storedCredential).isPresent();
        assertThat(storedCredential.get().getStatus()).isEqualTo(CredentialStatus.REVOKED);

        var auditEntries = auditEntryRepository.findAll();
        assertThat(auditEntries).isNotEmpty();
        assertThat(auditEntries.stream().map(entry -> entry.getAction()))
                .contains(AuditAction.ISSUED, AuditAction.VALIDATED, AuditAction.REVOKED, AuditAction.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST when issuing without identity verification")
    void shouldReturnBadRequest_whenIssueWithoutVerification() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SessionCredentialTestFixtures.issueRequestUnverified())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST when H3 minimal payload omits identityVerified (RN1)")
    void shouldReturnBadRequest_whenH3MinimalPayloadWithoutIdentityVerified() throws Exception {
        String payload = """
                {
                  "linkId": "%s",
                  "bankUserId": "%s"
                }
                """.formatted(SessionCredentialTestFixtures.CHANNEL_LINK_ID, SessionCredentialTestFixtures.BANK_USER_ID);

        mockMvc.perform(post("/api/v1/sessions/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
