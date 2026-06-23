package com.wa.banking.auth.repository;

import com.wa.banking.auth.entity.SessionAuditEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio MongoDB para entradas de auditoría de credenciales de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface SessionAuditEntryRepository extends MongoRepository<SessionAuditEntryEntity, String> {

    Page<SessionAuditEntryEntity> findByCredentialIdOrderByPerformedAtDesc(String credentialId, Pageable pageable);
}
