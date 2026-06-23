package com.wa.banking.accounts.service;

import com.wa.banking.accounts.api.v1.dto.AccountBalanceResponse;
import com.wa.banking.accounts.api.v1.dto.AccountListResponse;
import com.wa.banking.accounts.api.v1.dto.AccountResponse;
import com.wa.banking.accounts.entity.AccountEntity;
import com.wa.banking.accounts.exception.AccountNotFoundException;
import com.wa.banking.accounts.mapper.AccountMapper;
import com.wa.banking.accounts.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String BANK_USER_ID = "user-demo-001";

    private static final String OTHER_BANK_USER_ID = "user-other-002";

    private static final String ACCOUNT_ID = "acc-checking-001";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Test
    @DisplayName("Should return mapped accounts when listing by bankUserId")
    void shouldReturnMappedAccounts_whenListByBankUserId() {
        AccountEntity entity = sampleAccount(BANK_USER_ID);
        AccountResponse response = sampleResponse(ACCOUNT_ID);
        when(accountRepository.findByBankUserId(BANK_USER_ID)).thenReturn(List.of(entity));
        when(accountMapper.toResponseList(List.of(entity))).thenReturn(List.of(response));

        AccountListResponse result = accountService.listAccountsByBankUserId(BANK_USER_ID);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.accounts().getFirst().id()).isEqualTo(ACCOUNT_ID);
        verify(accountRepository).findByBankUserId(BANK_USER_ID);
    }

    @Test
    @DisplayName("Should return balance when account belongs to authenticated user")
    void shouldReturnBalance_whenAccountBelongsToUser() {
        AccountEntity entity = sampleAccount(BANK_USER_ID);
        AccountBalanceResponse balanceResponse = new AccountBalanceResponse(
                ACCOUNT_ID,
                "Cuenta Sueldo",
                "ARS",
                new BigDecimal("125000.50"),
                new BigDecimal("125000.50")
        );
        when(accountRepository.findByIdAndBankUserId(ACCOUNT_ID, BANK_USER_ID)).thenReturn(Optional.of(entity));
        when(accountMapper.toBalanceResponse(entity)).thenReturn(balanceResponse);

        AccountBalanceResponse result = accountService.getBalance(BANK_USER_ID, ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.availableBalance()).isEqualByComparingTo("125000.50");
        verify(accountRepository).findByIdAndBankUserId(ACCOUNT_ID, BANK_USER_ID);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account does not exist")
    void shouldThrowAccountNotFoundException_whenAccountDoesNotExist() {
        when(accountRepository.findByIdAndBankUserId("missing-id", BANK_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(BANK_USER_ID, "missing-id"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("missing-id");
    }

    @Test
    @DisplayName("Should enforce cross-user isolation when account belongs to another user")
    void shouldThrowAccountNotFoundException_whenAccountBelongsToAnotherUser() {
        when(accountRepository.findByIdAndBankUserId(ACCOUNT_ID, OTHER_BANK_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(OTHER_BANK_USER_ID, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID);
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void shouldReturnEmptyList_whenUserHasNoAccounts() {
        when(accountRepository.findByBankUserId(BANK_USER_ID)).thenReturn(List.of());
        when(accountMapper.toResponseList(List.of())).thenReturn(List.of());

        AccountListResponse result = accountService.listAccountsByBankUserId(BANK_USER_ID);

        assertThat(result.accounts()).isEmpty();
    }

    @Test
    @DisplayName("Should still return accounts when currency code is invalid")
    void shouldReturnAccounts_whenCurrencyIsInvalid() {
        AccountEntity invalidCurrencyAccount = sampleAccount(BANK_USER_ID);
        invalidCurrencyAccount.setCurrency("INVALID");
        AccountResponse response = sampleResponse(ACCOUNT_ID);
        when(accountRepository.findByBankUserId(BANK_USER_ID)).thenReturn(List.of(invalidCurrencyAccount));
        when(accountMapper.toResponseList(List.of(invalidCurrencyAccount))).thenReturn(List.of(response));

        AccountListResponse result = accountService.listAccountsByBankUserId(BANK_USER_ID);

        assertThat(result.accounts()).hasSize(1);
    }

    private AccountEntity sampleAccount(String bankUserId) {
        return AccountEntity.builder()
                .id(ACCOUNT_ID)
                .bankUserId(bankUserId)
                .alias("Cuenta Sueldo")
                .type("CHECKING")
                .currency("ARS")
                .availableBalance(new BigDecimal("125000.50"))
                .ledgerBalance(new BigDecimal("125000.50"))
                .status("ACTIVE")
                .build();
    }

    private AccountResponse sampleResponse(String accountId) {
        return new AccountResponse(
                accountId,
                "Cuenta Sueldo",
                "CHECKING",
                "ARS",
                new BigDecimal("125000.50"),
                new BigDecimal("125000.50"),
                "ACTIVE"
        );
    }
}
