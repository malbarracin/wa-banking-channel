package com.wa.banking.channel.repository;

import com.wa.banking.channel.entity.LinkAuditEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio de auditoría del ciclo de vida del vínculo de canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface LinkAuditEntryRepository extends MongoRepository<LinkAuditEntryEntity, String> {

    Page<LinkAuditEntryEntity> findByLinkIdOrderByPerformedAtDesc(String linkId, Pageable pageable);
}
