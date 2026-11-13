package com.bcra.ws.users.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bcra.ws.users.entity.UserAuditEventEntity;

/**
 * Acceso append-only a eventos de auditoría de usuarios.
 *
 * @since 2026-10-19
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
