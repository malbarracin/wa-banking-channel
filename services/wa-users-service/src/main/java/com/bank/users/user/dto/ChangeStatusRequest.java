package com.bank.users.user.dto;

import jakarta.validation.constraints.NotNull;

import com.bank.users.user.entity.UserStatus;

/** Estado operativo de destino; la baja lógica es terminal. */
public record ChangeStatusRequest(@NotNull UserStatus status) {
}
