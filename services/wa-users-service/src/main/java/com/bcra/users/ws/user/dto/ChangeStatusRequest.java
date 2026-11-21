package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(@NotNull UserStatus nuevoEstado, @NotBlank @Size(max = 2048) String motivo) { }
