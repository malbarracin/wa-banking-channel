package com.ingenia.wa.users.entity;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.ingenia.wa.users.domain.AuditAction;
import com.ingenia.wa.users.domain.AuditResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de auditoría append-only en la colección {@code user_audit_events}.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_audit_events")
public class UserAuditEventEntity {

    @Id
    private String id;

    @Indexed
    private String userId;

    private AuditAction action;

    private Instant timestamp;

    private AuditResult result;

    private Map<String, Object> summary;

    private String actor;
}
