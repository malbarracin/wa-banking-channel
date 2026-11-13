package com.bcra.ws.users.entity;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de auditoría append-only en la colección {@code user_audit_events}.
 *
 * @since 2026-10-19
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_audit_events")
public class UserAuditEventEntity {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    @Indexed
    private String userId;

    private AuditAction action;

    private Instant timestamp;

    private AuditResult result;

    private Map<String, Object> summary;

    private String actor;
}
