package com.wa.banking.accounts.integration.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCredentialValidatorTest {

    private static final String CREDENTIAL_ID = "cred-demo-001";

    private static final String TOKEN = "demo-token";

    @Mock
    private SessionValidationClient sessionValidationClient;

    @InjectMocks
    private SessionCredentialValidator sessionCredentialValidator;

    @Test
    @DisplayName("Should return true when H2 responds valid=true")
    void shouldReturnTrue_whenCredentialIsValid() {
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN)).thenReturn(validResponse());

        assertThat(sessionCredentialValidator.isValid(CREDENTIAL_ID, TOKEN)).isTrue();
        verify(sessionValidationClient).validate(CREDENTIAL_ID, TOKEN);
    }

    @Test
    @DisplayName("Should return false when H2 responds valid=false")
    void shouldReturnFalse_whenCredentialIsInvalid() {
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN))
                .thenReturn(ValidateCredentialResponse.builder().valid(false).build());

        assertThat(sessionCredentialValidator.isValid(CREDENTIAL_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("Should return false when H2 response is null")
    void shouldReturnFalse_whenResponseIsNull() {
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN)).thenReturn(null);

        assertThat(sessionCredentialValidator.isValid(CREDENTIAL_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("Should delegate validation to session client")
    void shouldDelegateToClient_whenValidateCalled() {
        ValidateCredentialResponse expected = validResponse();
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN)).thenReturn(expected);

        ValidateCredentialResponse result = sessionCredentialValidator.validate(CREDENTIAL_ID, TOKEN);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("Should propagate timeout when session client fails")
    void shouldPropagateException_whenSessionClientTimesOut() {
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> sessionCredentialValidator.validate(CREDENTIAL_ID, TOKEN))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("Should accept stub-mode response from session client")
    void shouldReturnTrue_whenStubModeReturnsValidCredential() {
        ValidateCredentialResponse stubResponse = ValidateCredentialResponse.builder()
                .valid(true)
                .bankUserId("user-demo-001")
                .channelLinkId("link-demo-001")
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        when(sessionValidationClient.validate(CREDENTIAL_ID, TOKEN)).thenReturn(stubResponse);

        assertThat(sessionCredentialValidator.isValid(CREDENTIAL_ID, TOKEN)).isTrue();
        assertThat(sessionCredentialValidator.validate(CREDENTIAL_ID, TOKEN).getBankUserId())
                .isEqualTo("user-demo-001");
    }

    private ValidateCredentialResponse validResponse() {
        return ValidateCredentialResponse.builder()
                .valid(true)
                .bankUserId("user-demo-001")
                .channelLinkId("link-demo-001")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }
}
