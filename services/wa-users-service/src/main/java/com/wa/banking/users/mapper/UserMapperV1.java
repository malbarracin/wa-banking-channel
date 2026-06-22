package com.wa.banking.users.mapper;

import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UserAuditResponseV1;
import com.wa.banking.users.api.v1.dto.UserResponseV1;
import com.wa.banking.users.entity.BankUserEntity;
import com.wa.banking.users.entity.UserAuditEntryEntity;
import com.wa.banking.users.entity.UserStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct v1 para conversiones entre entidades y DTOs de usuarios.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Mapper(componentModel = "spring")
public interface UserMapperV1 {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BankUserEntity toEntity(CreateUserRequestV1 request);

    @Mapping(target = "canLinkChannel", expression = "java(canLinkChannel(entity.getStatus()))")
    UserResponseV1 toResponse(BankUserEntity entity);

    UserAuditResponseV1 toAuditResponse(UserAuditEntryEntity entity);

    List<UserAuditResponseV1> toAuditResponseList(List<UserAuditEntryEntity> entities);

    default boolean canLinkChannel(UserStatus status) {
        return status == UserStatus.ACTIVE;
    }
}
