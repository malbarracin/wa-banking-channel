package com.bcra.users.ws.audit.dto;

import java.time.Instant;
import java.util.List;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;

public record AuditEventResponse(AuditAction action, AuditResult resultado, Instant fecha, String correlationId,
        String actor, List<String> resumen) {
}
