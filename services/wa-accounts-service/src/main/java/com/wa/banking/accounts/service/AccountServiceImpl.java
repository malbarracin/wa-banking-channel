package com.wa.banking.accounts.service;

import com.wa.banking.accounts.api.v1.dto.AccountBalanceResponse;
import com.wa.banking.accounts.api.v1.dto.AccountListResponse;
import com.wa.banking.accounts.entity.AccountEntity;
import com.wa.banking.accounts.exception.AccountNotFoundException;
import com.wa.banking.accounts.mapper.AccountMapper;
import com.wa.banking.accounts.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Implementación del servicio de cuentas con scope estricto por {@code bankUserId}.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Pattern ISO_CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final AccountRepository accountRepository;

    private final AccountMapper accountMapper;

    @Override
    public AccountListResponse listAccountsByBankUserId(String bankUserId) {
        List<AccountEntity> accounts = accountRepository.findByBankUserId(bankUserId);
        accounts.forEach(this::validateCurrency);
        log.info("Listed {} account(s) for bankUserId={}", accounts.size(), bankUserId);
        return new AccountListResponse(accountMapper.toResponseList(accounts));
    }

    @Override
    public AccountBalanceResponse getBalance(String bankUserId, String accountId) {
        AccountEntity account = accountRepository.findByIdAndBankUserId(accountId, bankUserId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        validateCurrency(account);
        log.info("Retrieved balance for accountId={} bankUserId={}", accountId, bankUserId);
        return accountMapper.toBalanceResponse(account);
    }

    private void validateCurrency(AccountEntity account) {
        String currency = account.getCurrency();
        if (currency == null || !ISO_CURRENCY.matcher(currency).matches()) {
            log.warn("Account {} has invalid currency code: {}", account.getId(), currency);
        }
    }
}
