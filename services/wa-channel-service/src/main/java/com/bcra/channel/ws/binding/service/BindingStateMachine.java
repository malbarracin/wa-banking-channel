package com.bcra.channel.ws.binding.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingConflictException;

/**
 * Transiciones permitidas del vínculo (tabla de arquitectura + re-vinculación desde BLOQUEADO).
 */
@Component
public class BindingStateMachine {

    private static final Map<BindingStatus, Set<BindingStatus>> ALLOWED = buildAllowed();

    /**
     * Valida una transición; lanza conflicto si no está permitida.
     *
     * @param from estado actual ({@link BindingStatus#SIN_VINCULO} si no hay registro)
     * @param to   estado destino
     */
    public void assertTransition(BindingStatus from, BindingStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!isAllowed(from, to)) {
            throw new BindingConflictException(
                    "Transición de estado no permitida: " + from + " → " + to);
        }
    }

    /**
     * Indica si la transición está permitida.
     *
     * @param from estado actual
     * @param to   estado destino
     * @return {@code true} si la tabla de transiciones lo permite
     */
    public boolean isAllowed(BindingStatus from, BindingStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Set<BindingStatus> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    private static Map<BindingStatus, Set<BindingStatus>> buildAllowed() {
        Map<BindingStatus, Set<BindingStatus>> map = new EnumMap<>(BindingStatus.class);
        map.put(BindingStatus.SIN_VINCULO, EnumSet.of(BindingStatus.PENDIENTE_VERIFICACION));
        map.put(
                BindingStatus.PENDIENTE_VERIFICACION,
                EnumSet.of(BindingStatus.ACTIVO, BindingStatus.BLOQUEADO));
        map.put(
                BindingStatus.ACTIVO,
                EnumSet.of(BindingStatus.BLOQUEADO, BindingStatus.DESVINCULADO));
        map.put(
                BindingStatus.BLOQUEADO,
                EnumSet.of(
                        BindingStatus.ACTIVO,
                        BindingStatus.DESVINCULADO,
                        BindingStatus.PENDIENTE_VERIFICACION));
        map.put(BindingStatus.DESVINCULADO, EnumSet.of(BindingStatus.PENDIENTE_VERIFICACION));
        return Map.copyOf(map);
    }
}
