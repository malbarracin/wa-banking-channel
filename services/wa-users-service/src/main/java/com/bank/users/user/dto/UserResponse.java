package com.bank.users.user.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.UserStatus;

/** Datos autorizados; channelLinkEligible se deriva del estado ACTIVE. */
public record UserResponse(UUID id, DocumentType documentType, String documentNumber, String displayName,
        String email, String phone, LocalDate birthDate, UserStatus status, Map<String, Object> preferences,
        boolean channelLinkEligible) {

    public UserResponse {
        preferences = Collections.unmodifiableMap(new LinkedHashMap<>(preferences));
    }

    @Override
    public String toString() {
        return "UserResponse[id=" + id + ", status=" + status + "]";
    }
}
