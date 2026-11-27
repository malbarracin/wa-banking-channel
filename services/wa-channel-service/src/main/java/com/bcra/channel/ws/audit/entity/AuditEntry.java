package com.bcra.channel.ws.audit.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrada de auditoría del canal WhatsApp.
 * {@code toString} omite número y detalle para no filtrar PII ni secretos.
 */
@Entity
@Table(name = "channel_audit_entries")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditEntry {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "binding_id", nullable = false)
    private UUID bindingId;

    @Column(name = "whatsapp_number", nullable = false, length = 32)
    private String whatsappNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AuditAction action;

    @Column(nullable = false, length = 32)
    private String result;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(length = 512)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Override
    public String toString() {
        return "AuditEntry{id=" + id
                + ", bindingId=" + bindingId
                + ", action=" + action
                + ", result=" + result
                + ", actor=" + actor
                + ", occurredAt=" + occurredAt
                + '}';
    }
}
