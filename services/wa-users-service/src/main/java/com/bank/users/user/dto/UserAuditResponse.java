package com.bank.users.user.dto;

import java.time.Instant;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditResult;

/** Evento resumido: fecha, acción, resultado y motivo controlado sin información personal. */
public record UserAuditResponse(Instant occurredAt, AuditAction action, AuditResult result, String detail) {
}
