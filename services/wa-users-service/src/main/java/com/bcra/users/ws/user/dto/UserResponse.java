package com.bcra.users.ws.user.dto;

import java.time.Instant;
import java.util.UUID;
import com.bcra.users.ws.user.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Datos autorizados por la política de privacidad y estado operativo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(UUID id, String externalId, String uniquenessValue, String displayName,
        String contactEmail, String contactPhone, UserStatus status, Instant createdAt) {
    @Override
    public String toString() {
        return "UserResponse[redacted]";
    }
}
