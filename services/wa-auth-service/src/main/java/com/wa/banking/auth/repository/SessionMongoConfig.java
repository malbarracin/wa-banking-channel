package com.wa.banking.auth.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.entity.SessionAuditEntryEntity;
import com.wa.banking.auth.entity.SessionCredentialEntity;

/**
 * Configuración de índices MongoDB y auditoría de entidades de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
public class SessionMongoConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        IndexOperations credentialIndexes = mongoTemplate.indexOps(SessionCredentialEntity.class);
        credentialIndexes.ensureIndex(new Index()
                .on("channelLinkId", org.springframework.data.domain.Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is(CredentialStatus.ACTIVE.name())))
                .named("idx_unique_active_channel_link"));
        credentialIndexes.ensureIndex(new Index()
                .on("bankUserId", org.springframework.data.domain.Sort.Direction.ASC)
                .named("idx_bank_user_id"));

        mongoTemplate.indexOps(SessionAuditEntryEntity.class)
                .ensureIndex(new Index()
                        .on("credentialId", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("idx_audit_credential_id"));
    }
}
