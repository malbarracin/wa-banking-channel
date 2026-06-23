package com.wa.banking.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Entrada de auditoría de operaciones sobre credenciales de sesión (sin secretos).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "session_audit_log")
public class SessionAuditEntryEntity {

    @Id
    private String id;

    @Indexed
    private String credentialId;

    @Indexed
    private String channelLinkId;

    private String bankUserId;

    private AuditAction action;

    private AuditActor actor;

    private String reason;

    private Instant performedAt;
}
