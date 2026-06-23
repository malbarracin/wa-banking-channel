package com.wa.banking.accounts.api.v1.controller;

import com.wa.banking.accounts.config.SecurityFilterConfig;
import com.wa.banking.accounts.entity.AccountEntity;
import com.wa.banking.accounts.integration.session.SessionCredentialValidator;
import com.wa.banking.accounts.integration.session.ValidateCredentialResponse;
import com.wa.banking.accounts.repository.AccountRepository;
import com.wa.banking.accounts.support.AbstractMongoIntegrationTest;
import com.wa.banking.accounts.support.TestAccountData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerIT extends AbstractMongoIntegrationTest {

    private static final String VALID_TOKEN = "demo-token";

    private static final String VALID_CREDENTIAL_ID = "cred-demo-001";

    private static final String UNAUTHORIZED_MESSAGE =
            "Por seguridad, verificá tu identidad para ver tus cuentas.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @SpyBean
    private SessionCredentialValidator sessionCredentialValidator;

    private String ownAccountId;

    @BeforeEach
    void setUp() {
        reset(sessionCredentialValidator);
        accountRepository.deleteAll();
        AccountEntity ownAccount = accountRepository.save(TestAccountData.demoAccountsForStubUser().getFirst());
        accountRepository.save(TestAccountData.demoAccountsForStubUser().get(1));
        accountRepository.save(TestAccountData.otherUserAccount());
        ownAccountId = ownAccount.getId();
    }

    @Test
    @DisplayName("Should return 401 when auth headers are missing")
    void shouldReturn401_whenAuthHeadersMissing() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 401 when session credential is invalid")
    void shouldReturn401_whenCredentialIsInvalid() throws Exception {
        doReturn(ValidateCredentialResponse.builder().valid(false).build())
                .when(sessionCredentialValidator).validate(anyString(), anyString());

        mockMvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .header(SecurityFilterConfig.CREDENTIAL_ID_HEADER, VALID_CREDENTIAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should return account list when credential is valid")
    void shouldReturn200WithSeedAccounts_whenCredentialIsValid() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/accounts")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].alias").value("Cuenta Sueldo"))
                .andExpect(jsonPath("$.accounts[0].currency").value("ARS"))
                .andExpect(jsonPath("$.accounts[1].alias").value("Caja de Ahorro USD"));
    }

    @Test
    @DisplayName("Should return balance for own account")
    void shouldReturn200WithBalance_whenAccountBelongsToAuthenticatedUser() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/accounts/{accountId}/balance", ownAccountId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ownAccountId))
                .andExpect(jsonPath("$.alias").value("Cuenta Sueldo"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.availableBalance").value(125000.50))
                .andExpect(jsonPath("$.ledgerBalance").value(125000.50));
    }

    @Test
    @DisplayName("Should return 404 when account does not exist for authenticated user")
    void shouldReturn404_whenAccountDoesNotExist() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/accounts/{accountId}/balance", "missing-account-id"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account not found: missing-account-id"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 404 when account belongs to another user")
    void shouldReturn404_whenAccountBelongsToAnotherUser() throws Exception {
        AccountEntity otherAccount = accountRepository.findByBankUserId(TestAccountData.OTHER_BANK_USER_ID).getFirst();

        mockMvc.perform(authorized(get("/api/v1/accounts/{accountId}/balance", otherAccount.getId()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .header(SecurityFilterConfig.CREDENTIAL_ID_HEADER, VALID_CREDENTIAL_ID);
    }
}
