package com.bcra.ws.users.api.v1.dto;

import com.bcra.ws.users.exception.NonUpdatableFieldException;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request de actualización de campos permitidos (U3).
 * Solo preferredName, email, phone, preferredLanguage y address.
 * Al menos un campo debe estar presente (validado en service).
 *
 * @since 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Campos actualizables del usuario (U3). Al menos uno requerido.")
public class UserUpdateRequest {

    @Size(max = 255)
    @Schema(description = "Dirección de contacto", example = "Av. Corrientes 1234")
    private String address;

    /** Rechaza campos ajenos al contrato incluso si Jackson ignora desconocidos globalmente. */
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new NonUpdatableFieldException(field);
    }

    @Size(max = 100)
    @Schema(
            description = "Nombre preferido para tratamiento",
            example = "Mari",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String preferredName;

    @Email(message = "email must be valid")
    @Size(max = 255)
    @Schema(
            description = "Email de contacto bancario",
            example = "mari@example.com",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Size(max = 32)
    @Schema(
            description = "Teléfono de contacto genérico",
            example = "+5491112345678",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phone;

    @Size(max = 16)
    @Schema(
            description = "Idioma preferido BCP-47",
            example = "es",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String preferredLanguage;
}
