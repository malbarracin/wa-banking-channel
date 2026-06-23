package com.wa.banking.channel.api.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.channel.api.error.GlobalExceptionHandler;
import com.wa.banking.channel.api.v1.dto.AcceptTermsRequestV1;
import com.wa.banking.channel.api.v1.dto.BlockLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InitiateLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.LinkResponseV1;
import com.wa.banking.channel.api.v1.dto.PreferencesRequestV1;
import com.wa.banking.channel.api.v1.dto.PreferencesResponseV1;
import com.wa.banking.channel.api.v1.dto.ProfileResponseV1;
import com.wa.banking.channel.api.v1.dto.RelinkRequestV1;
import com.wa.banking.channel.api.v1.dto.UnlinkRequestV1;
import com.wa.banking.channel.api.v1.dto.VerifyIdentityRequestV1;
import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.exception.DuplicateLinkException;
import com.wa.banking.channel.exception.LinkNotFoundException;
import com.wa.banking.channel.service.WhatsAppLinkService;
import com.wa.banking.channel.support.LinkTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WhatsAppLinkControllerV1.class)
@Import(GlobalExceptionHandler.class)
class WhatsAppLinkControllerV1Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WhatsAppLinkService linkService;

    private LinkResponseV1 sampleResponse() {
        return LinkResponseV1.builder()
                .id(LinkTestFixtures.LINK_ID)
                .phoneNumber(LinkTestFixtures.PHONE)
                .status(LinkStatus.NO_LINK)
                .build();
    }

    @Test
    @DisplayName("Should return 201 when initiating link")
    void shouldReturnCreated_whenInitiateLink() throws Exception {
        when(linkService.initiateLink(any(InitiateLinkRequestV1.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/channel-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.initiateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(LinkTestFixtures.LINK_ID));
    }

    @Test
    @DisplayName("Should return 400 VALIDATION_ERROR when phone invalid")
    void shouldReturnValidationError_whenPhoneInvalid() throws Exception {
        InitiateLinkRequestV1 request = InitiateLinkRequestV1.builder()
                .phoneNumber("invalid")
                .build();

        mockMvc.perform(post("/api/v1/channel-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when link not found by phone")
    void shouldReturnNotFound_whenPhoneNotFound() throws Exception {
        when(linkService.findByPhone(LinkTestFixtures.PHONE))
                .thenThrow(new LinkNotFoundException(LinkTestFixtures.PHONE));

        mockMvc.perform(get("/api/v1/channel-links/by-phone/{phone}", LinkTestFixtures.PHONE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Should return 409 when duplicate active link")
    void shouldReturnConflict_whenDuplicateActiveLink() throws Exception {
        when(linkService.initiateLink(any(InitiateLinkRequestV1.class)))
                .thenThrow(new DuplicateLinkException(LinkTestFixtures.PHONE));

        mockMvc.perform(post("/api/v1/channel-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.initiateRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Should accept terms on link")
    void shouldAcceptTerms_whenValidRequest() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setStatus(LinkStatus.PENDING_VERIFICATION);
        when(linkService.acceptTerms(eq(LinkTestFixtures.LINK_ID), any(AcceptTermsRequestV1.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/accept-terms", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.acceptTermsRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    @DisplayName("Should verify identity on link")
    void shouldVerifyIdentity_whenValidRequest() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setIdentityVerified(true);
        when(linkService.verifyIdentity(eq(LinkTestFixtures.LINK_ID), any(VerifyIdentityRequestV1.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/verify", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.verifyRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityVerified").value(true));
    }

    @Test
    @DisplayName("Should complete onboarding")
    void shouldCompleteOnboarding_whenLinkReady() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setStatus(LinkStatus.ACTIVE);
        when(linkService.completeOnboarding(LinkTestFixtures.LINK_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/complete-onboarding", LinkTestFixtures.LINK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Should find link by id")
    void shouldFindById_whenLinkExists() throws Exception {
        when(linkService.findById(LinkTestFixtures.LINK_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/channel-links/{id}", LinkTestFixtures.LINK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(LinkTestFixtures.PHONE));
    }

    @Test
    @DisplayName("Should return profile for active link")
    void shouldReturnProfile_whenActiveLink() throws Exception {
        ProfileResponseV1 profile = ProfileResponseV1.builder()
                .linkId(LinkTestFixtures.LINK_ID)
                .displayName("María García")
                .maskedEmail("m***@example.com")
                .maskedPhone("+541****5678")
                .language("es")
                .build();
        when(linkService.getProfile(LinkTestFixtures.LINK_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/channel-links/{id}/profile", LinkTestFixtures.LINK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("María García"));
    }

    @Test
    @DisplayName("Should get and update preferences")
    void shouldGetAndUpdatePreferences_whenActiveLink() throws Exception {
        PreferencesResponseV1 preferences = PreferencesResponseV1.builder()
                .language("es")
                .notificationsEnabled(true)
                .build();
        when(linkService.getPreferences(LinkTestFixtures.LINK_ID)).thenReturn(preferences);
        PreferencesResponseV1 updated = PreferencesResponseV1.builder()
                .language("en")
                .notificationsEnabled(true)
                .build();
        when(linkService.updatePreferences(eq(LinkTestFixtures.LINK_ID), any(PreferencesRequestV1.class)))
                .thenReturn(updated);

        mockMvc.perform(get("/api/v1/channel-links/{id}/preferences", LinkTestFixtures.LINK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("es"));

        PreferencesRequestV1 update = PreferencesRequestV1.builder().language("en").build();
        mockMvc.perform(patch("/api/v1/channel-links/{id}/preferences", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    @DisplayName("Should block link")
    void shouldBlockLink_whenConfirmed() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setStatus(LinkStatus.BLOCKED);
        when(linkService.block(eq(LinkTestFixtures.LINK_ID), any(BlockLinkRequestV1.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/block", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.blockRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    @DisplayName("Should unlink link")
    void shouldUnlinkLink_whenConfirmed() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setStatus(LinkStatus.UNLINKED);
        when(linkService.unlink(eq(LinkTestFixtures.LINK_ID), any(UnlinkRequestV1.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/unlink", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.unlinkRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLINKED"));
    }

    @Test
    @DisplayName("Should relink unlinked number")
    void shouldRelink_whenUnlinked() throws Exception {
        LinkResponseV1 response = sampleResponse();
        response.setStatus(LinkStatus.NO_LINK);
        when(linkService.relink(eq(LinkTestFixtures.LINK_ID), any(RelinkRequestV1.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/channel-links/{id}/relink", LinkTestFixtures.LINK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LinkTestFixtures.relinkRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_LINK"));
    }

    @Test
    @DisplayName("Should return interaction history")
    void shouldReturnHistory_whenLinkExists() throws Exception {
        when(linkService.getHistory(eq(LinkTestFixtures.LINK_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/channel-links/{id}/history", LinkTestFixtures.LINK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
