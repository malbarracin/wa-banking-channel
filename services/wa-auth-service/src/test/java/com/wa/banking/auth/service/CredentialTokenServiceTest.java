package com.wa.banking.auth.service;

import com.wa.banking.auth.integration.SessionCredentialProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialTokenServiceTest {

    private CredentialTokenService tokenService;

    @BeforeEach
    void setUp() {
        SessionCredentialProperties properties = new SessionCredentialProperties();
        properties.setPepper("test-pepper");
        tokenService = new CredentialTokenService(properties);
    }

    @Test
    @DisplayName("Should generate token with sess prefix")
    void shouldGenerateToken_whenCalled() {
        String token = tokenService.generateToken();

        assertThat(token).startsWith("sess_");
        assertThat(token.length()).isGreaterThan(20);
    }

    @Test
    @DisplayName("Should hash token deterministically with pepper")
    void shouldHashToken_whenSameInput() {
        String hash1 = tokenService.hashToken("my-token");
        String hash2 = tokenService.hashToken("my-token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("Should match token against stored hash")
    void shouldMatchToken_whenHashCorrect() {
        String token = "sess_test_abc";
        String hash = tokenService.hashToken(token);

        assertThat(tokenService.matches(token, hash)).isTrue();
    }

    @Test
    @DisplayName("Should not match when token is wrong")
    void shouldNotMatch_whenTokenWrong() {
        String hash = tokenService.hashToken("correct-token");

        assertThat(tokenService.matches("wrong-token", hash)).isFalse();
    }

    @Test
    @DisplayName("Should not match when token or hash is null")
    void shouldNotMatch_whenNullArguments() {
        assertThat(tokenService.matches(null, "hash")).isFalse();
        assertThat(tokenService.matches("token", null)).isFalse();
    }
}
