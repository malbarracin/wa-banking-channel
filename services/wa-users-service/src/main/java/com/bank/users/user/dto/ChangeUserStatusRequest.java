package com.bank.users.user.dto;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.bank.users.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado operativo solicitado; solo se aceptan las transiciones habilitadas en H1.
 */
@Schema(description = "Solicitud de cambio de estado operativo")
public record ChangeUserStatusRequest(
        @Schema(description = "Nuevo estado operativo", example = "SUSPENDIDO",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ACTIVO", "SUSPENDIDO", "BAJA_LOGICA"})
        @NotNull UserStatus status) {

    /**
     * Rechaza ordinales numéricos y campos ajenos al contrato de cambio de estado.
     */
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
