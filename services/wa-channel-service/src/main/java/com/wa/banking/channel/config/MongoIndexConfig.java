package com.wa.banking.channel.config;

import com.wa.banking.channel.entity.LinkAuditEntryEntity;
import com.wa.banking.channel.entity.InteractionHistoryEntity;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Configuración de índices MongoDB y auditoría de entidades del dominio canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
@EnableMongoAuditing
@EnableConfigurationProperties(IntegrationProperties.class)
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        IndexOperations linkIndexes = mongoTemplate.indexOps(WhatsAppLinkEntity.class);
        linkIndexes.ensureIndex(new Index().on("phoneNumber", org.springframework.data.domain.Sort.Direction.ASC)
                .named("idx_phone_number"));
        linkIndexes.ensureIndex(new Index().on("bankUserId", org.springframework.data.domain.Sort.Direction.ASC)
                .named("idx_bank_user_id"));
        linkIndexes.ensureIndex(new Index()
                .on("phoneNumber", org.springframework.data.domain.Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("ACTIVE")))
                .named("idx_unique_active_phone"));

        mongoTemplate.indexOps(LinkAuditEntryEntity.class)
                .ensureIndex(new Index().on("linkId", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("idx_audit_link_id"));

        mongoTemplate.indexOps(InteractionHistoryEntity.class)
                .ensureIndex(new Index().on("linkId", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("idx_interaction_link_id"));
    }
}
