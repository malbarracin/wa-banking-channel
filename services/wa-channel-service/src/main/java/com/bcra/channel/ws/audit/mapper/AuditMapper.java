package com.bcra.channel.ws.audit.mapper;

import org.mapstruct.Mapper;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.entity.AuditEntry;

/**
 * Mapeo Entity ↔ DTO del módulo de auditoría.
 */
@Mapper(componentModel = "spring")
public interface AuditMapper {

    AuditEntryResponse toResponse(AuditEntry entry);
}
