package com.bank.users.user.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bank.users.user.entity.UserStatus;

/** Estado operativo de destino; la baja lógica es terminal. */
public record ChangeStatusRequest(
        @NotNull
        @Schema(description = "Estado operativo al que se transita",
                example = "SUSPENDED",
                allowableValues = {"ACTIVE", "SUSPENDED", "LOGICALLY_DELETED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status) {
}
