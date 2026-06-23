package com.wa.banking.accounts.support;

import com.wa.banking.accounts.entity.AccountEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de prueba alineados con el seed demo de cuentas.
 */
public final class TestAccountData {

    public static final String DEMO_BANK_USER_ID = "user-demo-001";

    public static final String OTHER_BANK_USER_ID = "user-other-002";

    private TestAccountData() {
    }

    public static List<AccountEntity> demoAccountsForStubUser() {
        return List.of(
                AccountEntity.builder()
                        .bankUserId(DEMO_BANK_USER_ID)
                        .alias("Cuenta Sueldo")
                        .type("CHECKING")
                        .currency("ARS")
                        .availableBalance(new BigDecimal("125000.50"))
                        .ledgerBalance(new BigDecimal("125000.50"))
                        .status("ACTIVE")
                        .build(),
                AccountEntity.builder()
                        .bankUserId(DEMO_BANK_USER_ID)
                        .alias("Caja de Ahorro USD")
                        .type("SAVINGS")
                        .currency("USD")
                        .availableBalance(new BigDecimal("1500.00"))
                        .ledgerBalance(new BigDecimal("1500.00"))
                        .status("ACTIVE")
                        .build()
        );
    }

    public static AccountEntity otherUserAccount() {
        return AccountEntity.builder()
                .bankUserId(OTHER_BANK_USER_ID)
                .alias("Cuenta Ajena")
                .type("CHECKING")
                .currency("ARS")
                .availableBalance(new BigDecimal("500.00"))
                .ledgerBalance(new BigDecimal("500.00"))
                .status("ACTIVE")
                .build();
    }
}
