package com.wa.banking.accounts.mapper;

import com.wa.banking.accounts.api.v1.dto.AccountBalanceResponse;
import com.wa.banking.accounts.api.v1.dto.AccountResponse;
import com.wa.banking.accounts.entity.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para conversiones entre {@link AccountEntity} y DTOs de API.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(AccountEntity entity);

    List<AccountResponse> toResponseList(List<AccountEntity> entities);

    @Mapping(source = "id", target = "accountId")
    AccountBalanceResponse toBalanceResponse(AccountEntity entity);
}
