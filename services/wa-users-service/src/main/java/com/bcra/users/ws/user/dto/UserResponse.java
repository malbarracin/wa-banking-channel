package com.bcra.users.ws.user.dto;

import java.time.Instant;
import java.util.UUID;
import com.bcra.users.ws.user.entity.DocumentType;
import com.bcra.users.ws.user.entity.UserStatus;

public record UserResponse(UUID id, DocumentType tipoDocumento, String numeroDocumentoEnmascarado,
        String nombreCompleto, UserStatus estado, String email, String telefono, Instant createdAt, Instant updatedAt) {
}
