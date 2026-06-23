package com.wa.banking.auth.api.v1.mapper;

import com.wa.banking.auth.api.v1.dto.AuditEntryResponse;
import com.wa.banking.auth.api.v1.dto.CredentialStatusResponse;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RenewCredentialResponse;
import com.wa.banking.auth.entity.SessionAuditEntryEntity;
import com.wa.banking.auth.entity.SessionCredentialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct entre entidades de credencial y DTOs de la API v1.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Mapper(componentModel = "spring")
public interface SessionCredentialMapper {

    @Mapping(target = "credentialId", source = "id")
    CredentialStatusResponse toStatusResponse(SessionCredentialEntity entity);

    @Mapping(target = "credentialId", source = "entity.id")
    @Mapping(target = "token", source = "token")
    IssueCredentialResponse toIssueResponse(SessionCredentialEntity entity, String token);

    @Mapping(target = "credentialId", source = "id")
    RenewCredentialResponse toRenewResponse(SessionCredentialEntity entity);

    AuditEntryResponse toAuditResponse(SessionAuditEntryEntity entity);

    List<AuditEntryResponse> toAuditResponseList(List<SessionAuditEntryEntity> entities);
}
