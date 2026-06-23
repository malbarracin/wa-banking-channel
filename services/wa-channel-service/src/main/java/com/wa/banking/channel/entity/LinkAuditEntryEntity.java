package com.wa.banking.channel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Entrada de auditoría del ciclo de vida del vínculo de canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "link_audit_log")
public class LinkAuditEntryEntity {

    @Id
    private String id;

    @Indexed
    private String linkId;

    private AuditAction action;

    private String actor;

    private String result;

    private String details;

    @CreatedDate
    private Instant performedAt;
}
