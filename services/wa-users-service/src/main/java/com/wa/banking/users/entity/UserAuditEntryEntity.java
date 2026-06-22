package com.wa.banking.users.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Entrada de auditoría para operaciones sobre usuarios bancarios.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_audit_log")
public class UserAuditEntryEntity {

    @Id
    private String id;

    private String userId;

    private AuditAction action;

    private UserStatus previousStatus;

    private UserStatus newStatus;

    private List<String> changedFields;

    private Instant performedAt;

    private String result;
}
