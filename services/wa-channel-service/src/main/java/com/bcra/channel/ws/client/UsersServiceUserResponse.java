package com.bcra.channel.ws.client;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de {@code UserResponse} de wa-users-service ({@code GET /api/v1/users/{id}}).
 * Campos alineados al openapi de H1; no se expone como API propia del canal.
 */
record UsersServiceUserResponse(
        UUID id,
        String externalId,
        String displayName,
        String contactEmail,
        String contactPhone,
        UsersServiceUserStatus status,
        Instant createdAt) {

    @Override
    public String toString() {
        return "UsersServiceUserResponse[id=" + id + ", status=" + status + "]";
    }
}
