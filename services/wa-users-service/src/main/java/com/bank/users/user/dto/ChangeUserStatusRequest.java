package com.bank.users.user.dto;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.bank.users.user.entity.UserStatus;

/** Estado operativo solicitado; solo se aceptan las transiciones habilitadas en H1. */
public record ChangeUserStatusRequest(@NotNull UserStatus status) {

    /** Rechaza ordinales numéricos y campos ajenos al contrato de cambio de estado. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ChangeUserStatusRequest fromJson(Map<String, Object> fields) {
        if (!Set.of("status").containsAll(fields.keySet())) {
            throw new IllegalArgumentException("La solicitud contiene campos no permitidos");
        }
        Object value = fields.get("status");
        if (value == null) {
            return new ChangeUserStatusRequest(null);
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("El estado debe ser texto");
        }
        return new ChangeUserStatusRequest(UserStatus.valueOf(text));
    }
}
