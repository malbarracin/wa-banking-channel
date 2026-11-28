package com.bcra.users.ws.audit.entity;

import java.time.Instant;
import java.util.UUID;

import com.bcra.users.ws.user.entity.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditResult result;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private UserStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 32)
    private UserStatus newStatus;
    @Column(name = "changed_fields", length = 1024)
    private String changedFields;
    @Column(nullable = false)
    private String actor;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AuditEntry(UUID userId, AuditAction action, AuditResult result, UserStatus previousStatus,
            UserStatus newStatus, String changedFields, String actor, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = action;
        this.result = result;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedFields = changedFields;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }
}
