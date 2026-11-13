package com.bcra.ws.users.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.repository.UserAuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio append-only de auditoría de usuarios.
 *
 * @since 2026-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private static final String DEFAULT_ACTOR = "SYSTEM";

    private final UserAuditEventRepository auditEventRepository;

    /**
     * Registra un evento de auditoría exitoso o fallido.
     *
     * @param userId  id del usuario (puede ser null en fallos pre-persistencia)
     * @param action  acción
     * @param result  resultado
     * @param summary mapa resumen (campos tocados / estados)
     * @param actor   actor opcional (header X-Actor-Id); si null → SYSTEM
     * @return evento persistido
     */
    public UserAuditEventEntity record(
            String userId,
            AuditAction action,
            AuditResult result,
            Map<String, Object> summary,
            String actor) {
        UserAuditEventEntity event = UserAuditEventEntity.builder()
                .userId(userId)
                .action(action)
                .timestamp(Instant.now())
                .result(result)
                .summary(summary == null ? Map.of() : Map.copyOf(summary))
                .actor(resolveActor(actor))
                .build();

        UserAuditEventEntity saved = auditEventRepository.save(event);
        log.debug("Audit event recorded: action={}, userId={}, result={}", action, userId, result);
        return saved;
    }

    /**
     * Lista historial de un usuario (más reciente primero).
     *
     * @param userId id interno
     * @return eventos
     */
    public List<UserAuditEventEntity> findByUserId(String userId) {
        return List.copyOf(auditEventRepository.findByUserIdOrderByTimestampDesc(userId));
    }

    private static String resolveActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return DEFAULT_ACTOR;
        }
        return actor.trim();
    }
}
