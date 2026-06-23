package com.wa.banking.accounts.integration.session;

import com.wa.banking.accounts.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionValidationClientTest {

    private static final String CREDENTIAL_ID = "cred-demo-001";

    private static final String TOKEN = "demo-token";

    @Mock
    private RestClient sessionRestClient;

    private IntegrationProperties integrationProperties;

    private SessionValidationClient sessionValidationClient;

    @BeforeEach
    void setUp() {
        integrationProperties = new IntegrationProperties();
        sessionValidationClient = new SessionValidationClient(integrationProperties, sessionRestClient);
    }

    @Test
    @DisplayName("Should return stub credential when stub mode is enabled")
    void shouldReturnStubCredential_whenStubModeEnabled() {
        integrationProperties.getSession().setStubEnabled(true);

        ValidateCredentialResponse response = sessionValidationClient.validate(CREDENTIAL_ID, TOKEN);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getBankUserId()).isEqualTo("user-demo-001");
        assertThat(response.getChannelLinkId()).isEqualTo("link-demo-001");
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should delegate to RestClient when stub mode is disabled")
    void shouldDelegateToRestClient_whenStubModeDisabled() {
        integrationProperties.getSession().setStubEnabled(false);
        RestClient.RequestBodyUriSpec requestBodyUriSpec = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        ValidateCredentialResponse remoteResponse = ValidateCredentialResponse.builder()
                .valid(true)
                .bankUserId("remote-user")
                .build();

        when(sessionRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/v1/sessions/credentials/validate")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(ValidateCredentialRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ValidateCredentialResponse.class)).thenReturn(remoteResponse);

        ValidateCredentialResponse response = sessionValidationClient.validate(CREDENTIAL_ID, TOKEN);

        assertThat(response.getBankUserId()).isEqualTo("remote-user");
    }
}
