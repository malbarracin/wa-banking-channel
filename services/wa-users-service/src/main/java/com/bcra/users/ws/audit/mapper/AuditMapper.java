package com.bcra.users.ws.audit.mapper;

import com.bcra.users.ws.audit.dto.AuditEntryResponse;
import com.bcra.users.ws.audit.entity.AuditEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditMapper {
    AuditEntryResponse toResponse(AuditEntry entry);
}
