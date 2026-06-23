package com.wa.banking.accounts.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Entidad de cuenta bancaria persistida en MongoDB.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts")
@CompoundIndex(name = "idx_bank_user_alias", def = "{'bankUserId': 1, 'alias': 1}")
public class AccountEntity {

    @Id
    private String id;

    private String bankUserId;

    private String alias;

    private String type;

    private String currency;

    private BigDecimal availableBalance;

    private BigDecimal ledgerBalance;

    private String status;
}
