package com.bcra.ws.users.api.v1.dto;

import com.bcra.ws.users.domain.DocumentType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request de alta de usuario (U1). Campos mínimos Compliance MVP.
 *
 * @since 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos mínimos para alta de usuario (U1)")
public class UserCreateRequest {

    @NotNull(message = "documentType is required")
    @Schema(
            description = "Tipo de documento (parte de la clave de unicidad)",
            example = "DNI",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private DocumentType documentType;

    @NotBlank(message = "documentNumber is required")
    @Size(max = 32)
    @Schema(
            description = "Número de documento (parte de la clave de unicidad)",
            example = "30111222",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentNumber;

    @NotBlank(message = "firstName is required")
    @Size(max = 100)
    @Schema(
            description = "Nombre legal de registro (no actualizable en U3)",
            example = "Maria",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;

    @NotBlank(message = "lastName is required")
    @Size(max = 100)
    @Schema(
            description = "Apellido legal de registro (no actualizable en U3)",
            example = "Lopez",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 255)
    @Schema(
            description = "Email de contacto bancario",
            example = "maria.lopez@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Size(max = 32)
    @Schema(
            description = "Teléfono de contacto genérico (no es vínculo WhatsApp)",
            example = "+5491112345678",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phone;

    @Size(max = 100)
    @Schema(
            description = "Nombre preferido para tratamiento",
            example = "Mari",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String preferredName;

    @Size(max = 16)
    @Schema(
            description = "Idioma preferido BCP-47 (default es)",
            example = "es",
            defaultValue = "es",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String preferredLanguage;
}
