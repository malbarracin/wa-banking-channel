package com.bank.users.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.user.entity.AuditEvent;

/**
 * Persistencia de eventos de auditoría del ciclo de vida del usuario.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Devuelve los eventos de un usuario, del más reciente al más antiguo.
     *
     * @param userId identificador interno del usuario
     * @return eventos ordenados por {@code occurredAt} descendente; lista vacía si no hay
     */
    List<AuditEvent> findByUserIdOrderByOccurredAtDesc(Long userId);
}
