package com.bank.users.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.bank.users.user.dto.AuditEventType;
import com.bank.users.user.dto.AuditRejectionReason;
import com.bank.users.user.dto.AuditResult;

/**
 * Evento de auditoría del ciclo de vida de un usuario. Es inmutable una vez creado (sin setters).
 *
 * <p>Solo guarda el identificador interno del usuario y metadatos (tipo, estados, nombres de campos,
 * resultado, motivo codificado y fecha/hora): nunca documento, email, nombres personales, credenciales
 * ni payloads. {@code userId} es una referencia lógica sin FK y es {@code null} en
 * {@link AuditEventType#ALTA_RECHAZADA}, que no crea usuario.
 */
@Entity
@Table(name = "audit_events", indexes = @Index(name = "idx_audit_events_user_occurred",
        columnList = "user_id, occurred_at"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class AuditEvent {

    private static final int ENUM_COLUMN_LENGTH = 30;
    private static final int MODIFIED_FIELDS_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = ENUM_COLUMN_LENGTH)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = ENUM_COLUMN_LENGTH)
    private UserStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = ENUM_COLUMN_LENGTH)
    private UserStatus newStatus;

    /** Nombres de campos modificados separados por coma (sin valores). */
    @Column(name = "modified_fields", length = MODIFIED_FIELDS_LENGTH)
    private String modifiedFields;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = ENUM_COLUMN_LENGTH)
    private AuditResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = ENUM_COLUMN_LENGTH)
    private AuditRejectionReason reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
