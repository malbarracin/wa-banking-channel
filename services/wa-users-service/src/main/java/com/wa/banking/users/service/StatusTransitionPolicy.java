package com.wa.banking.users.service;

import com.wa.banking.users.entity.UserStatus;
import com.wa.banking.users.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Política de transiciones de estado operativo para usuarios bancarios.
 * SOFT_DELETED es terminal y no admite reactivación en H1.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Component
public class StatusTransitionPolicy {

    private static final Map<UserStatus, Set<UserStatus>> ALLOWED_TRANSITIONS = Map.of(
            UserStatus.ACTIVE, Set.of(UserStatus.SUSPENDED, UserStatus.SOFT_DELETED),
            UserStatus.SUSPENDED, Set.of(UserStatus.ACTIVE, UserStatus.SOFT_DELETED),
            UserStatus.SOFT_DELETED, Set.of()
    );

    /**
     * Valida que la transición de estado solicitada esté permitida.
     *
     * @param currentStatus estado actual del usuario
     * @param targetStatus  estado destino solicitado
     * @throws InvalidStatusTransitionException si la transición no está permitida
     */
    public void validateTransition(UserStatus currentStatus, UserStatus targetStatus) {
        if (currentStatus == targetStatus) {
            throw new InvalidStatusTransitionException(
                    "User is already in status " + targetStatus.name());
        }

        Set<UserStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new InvalidStatusTransitionException(
                    "Transition from " + currentStatus.name() + " to " + targetStatus.name() + " is not allowed");
        }
    }
}
