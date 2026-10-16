package com.bank.users.user.dto;

import java.util.UUID;

import com.bank.users.user.entity.UserStatus;

/** Contrato mínimo para consumidores; la aptitud es verdadera exclusivamente para usuarios activos. */
public record UserChannelEligibilityResponse(UUID userId, UserStatus status, boolean aptoParaVinculoCanal) {
}
