package com.wa.banking.channel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.channel.entity.DocumentType;
import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.integration.session.SessionClient;
import com.wa.banking.channel.integration.session.SessionCredentialResponse;
import com.wa.banking.channel.integration.users.UserResponseV1;
import com.wa.banking.channel.integration.users.UsersClient;
import com.wa.banking.channel.repository.WhatsAppLinkRepository;
import com.wa.banking.channel.support.LinkTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class WhatsAppLinkIntegrationTest {

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
    private WhatsAppLinkRepository linkRepository;

    @MockBean
    private UsersClient usersClient;

    @MockBean
    private SessionClient sessionClient;

    @BeforeEach
    void cleanDatabase() {
        linkRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete F1 onboarding end-to-end with Mongo and mocked H1/H2")
    void shouldCompleteOnboardingFlow_whenValidSteps() throws Exception {
        when(usersClient.findByDocument(eq(DocumentType.DNI), eq("12345678")))
                .thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(sessionClient.issueCredential(anyString(), eq(LinkTestFixtures.BANK_USER_ID)))
                .thenReturn(LinkTestFixtures.sessionCredential());

        String linkId = initiateLink();
        acceptTerms(linkId);
        verifyIdentity(linkId);
        completeOnboarding(linkId);

        mockMvc.perform(get("/api/v1/channel-links/{id}", linkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.identityVerified").value(true));

        verify(sessionClient).issueCredential(linkId, LinkTestFixtures.BANK_USER_ID);
    }

    @Test
    @DisplayName("Should return conflict when initiating duplicate active link")
    void shouldReturnConflict_whenDuplicateActiveLink() throws Exception {
        when(usersClient.findByDocument(any(), anyString()))
                .thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(sessionClient.issueCredential(anyString(), anyString()))
                .thenReturn(LinkTestFixtures.sessionCredential());

        String linkId = initiateLink();
        acceptTerms(linkId);
        verifyIdentity(linkId);
        completeOnboarding(linkId);

        mockMvc.perform(post("/api/v1/channel-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.initiateRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Should revoke H2 credential when blocking active link")
    void shouldRevokeCredential_whenBlockActiveLink() throws Exception {
        when(usersClient.findByDocument(any(), anyString()))
                .thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(sessionClient.issueCredential(anyString(), anyString()))
                .thenReturn(LinkTestFixtures.sessionCredential());

        String linkId = initiateLink();
        acceptTerms(linkId);
        verifyIdentity(linkId);
        completeOnboarding(linkId);

        mockMvc.perform(post("/api/v1/channel-links/{id}/block", linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.blockRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(sessionClient).revokeCredential(LinkTestFixtures.CREDENTIAL_ID);

        var entity = linkRepository.findById(linkId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getStatus()).isEqualTo(LinkStatus.BLOCKED);
        assertThat(entity.get().getSessionCredentialId()).isNull();
    }

    @Test
    @DisplayName("Should return 404 when link not found by phone")
    void shouldReturnNotFound_whenPhoneMissing() throws Exception {
        mockMvc.perform(get("/api/v1/channel-links/by-phone/{phone}", LinkTestFixtures.PHONE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String initiateLink() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/channel-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.initiateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NO_LINK"))
                .andReturn();
        return readId(result);
    }

    private void acceptTerms(String linkId) throws Exception {
        mockMvc.perform(post("/api/v1/channel-links/{id}/accept-terms", linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.acceptTermsRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    private void verifyIdentity(String linkId) throws Exception {
        mockMvc.perform(post("/api/v1/channel-links/{id}/verify", linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.verifyRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityVerified").value(true));
    }

    private void completeOnboarding(String linkId) throws Exception {
        mockMvc.perform(post("/api/v1/channel-links/{id}/complete-onboarding", linkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }
}
