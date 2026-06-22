package com.wa.banking.users.repository;

import com.wa.banking.users.entity.UserAuditEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Spring Data MongoDB para entradas de auditoría de usuarios.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface UserAuditRepository extends MongoRepository<UserAuditEntryEntity, String> {

    Page<UserAuditEntryEntity> findByUserIdOrderByPerformedAtDesc(String userId, Pageable pageable);
}
