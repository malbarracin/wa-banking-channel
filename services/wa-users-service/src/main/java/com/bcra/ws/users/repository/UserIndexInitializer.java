package com.bcra.ws.users.repository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.bcra.ws.users.entity.UserEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asegura el índice único {@code (documentType, documentNumber)} al arrancar,
 * sin depender de {@code spring.data.mongodb.auto-index-creation}.
 *
 * @since 2026-10-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserIndexInitializer implements ApplicationRunner {

    private static final String INDEX_NAME = "uk_document";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Index index = new Index()
                .named(INDEX_NAME)
                .on("documentType", Sort.Direction.ASC)
                .on("documentNumber", Sort.Direction.ASC)
                .unique();
        String indexName = mongoTemplate.indexOps(UserEntity.class).ensureIndex(index);
        log.info("Ensured unique user document index: {}", indexName);
        mongoTemplate.indexOps(UserEntity.class).ensureIndex(
                new Index().named("uk_email").on("email", Sort.Direction.ASC).unique());
    }
}
