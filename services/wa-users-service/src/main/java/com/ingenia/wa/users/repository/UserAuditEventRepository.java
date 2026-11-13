package com.ingenia.wa.users.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ingenia.wa.users.entity.UserAuditEventEntity;

/**
 * Acceso append-only a eventos de auditoría de usuarios.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public interface UserAuditEventRepository extends MongoRepository<UserAuditEventEntity, String> {

    /**
     * Lista eventos de un usuario ordenados del más reciente al más antiguo.
     *
     * @param userId id interno del usuario
     * @return historial de auditoría
     */
    List<UserAuditEventEntity> findByUserIdOrderByTimestampDesc(String userId);
}
