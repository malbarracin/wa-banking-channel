package com.wa.banking.accounts.service;

import com.wa.banking.accounts.api.v1.dto.AccountBalanceResponse;
import com.wa.banking.accounts.api.v1.dto.AccountListResponse;

/**
 * Contrato de servicio para consulta de cuentas del titular autenticado.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface AccountService {

    AccountListResponse listAccountsByBankUserId(String bankUserId);

    AccountBalanceResponse getBalance(String bankUserId, String accountId);
}
