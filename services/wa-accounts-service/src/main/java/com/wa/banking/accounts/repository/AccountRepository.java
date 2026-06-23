package com.wa.banking.accounts.repository;

import com.wa.banking.accounts.entity.AccountEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data MongoDB para cuentas bancarias.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface AccountRepository extends MongoRepository<AccountEntity, String> {

    List<AccountEntity> findByBankUserId(String bankUserId);

    Optional<AccountEntity> findByIdAndBankUserId(String id, String bankUserId);
}
