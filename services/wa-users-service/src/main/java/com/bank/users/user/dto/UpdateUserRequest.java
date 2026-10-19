package com.bank.users.user.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bank.users.user.exception.InvalidUserUpdateException;
import com.bank.users.user.exception.NonUpdatableFieldException;

/**
 * Patch parcial: la presencia de una clave distingue omisión de eliminación explícita con null.
 * Las preferencias reemplazan el mapa completo; null lo vacía. No se valida un catálogo de claves.
 * El JSON del body es plano ({@code displayName}, {@code email}, {@code phone}, {@code preferences}).
 */
@Schema(description = "Actualización parcial de datos permitidos. Claves: displayName, email, phone, "
        + "preferences. Ausente = omitir; null = eliminar. Debe quedar al menos un contacto.",
        example = "{\"displayName\":\"Nuevo nombre\",\"preferences\":{\"language\":\"es\"}}")
public record UpdateUserRequest(
        @Schema(hidden = true)
        Map<String, Object> fields) {

    private static final Set<String> ALLOWED_FIELDS = Set.of("displayName", "email", "phone", "preferences");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public UpdateUserRequest {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        for (var entry : fields.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new NonUpdatableFieldException(field);
            }
            if (value != null
                    && !(field.equals("preferences") ? value instanceof Map<?, ?> : value instanceof String)) {
                throw new InvalidUserUpdateException(field, "Tipo de dato inválido");
            }
        }
    }

    /** Indica si el patch incluye explícitamente el campo indicado. */
    @Schema(hidden = true)
    public boolean hasField(String field) {
        return fields.containsKey(field);
    }

    /** Valor de {@code displayName} presente en el patch, o {@code null} si no se envía. */
    @Size(max = 120)
    @Schema(hidden = true)
    public String getDisplayName() {
        return (String) fields.get("displayName");
    }

    /** Valor de {@code email} presente en el patch, o {@code null} si no se envía. */
    @Email
    @Size(max = 254)
    @Pattern(regexp = "(?s).*\\S.*")
    @Schema(hidden = true)
    public String getEmail() {
        return (String) fields.get("email");
    }

    /** Valor de {@code phone} presente en el patch, o {@code null} si no se envía. */
    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}[0-9]")
    @Schema(hidden = true)
    public String getPhone() {
        return (String) fields.get("phone");
    }

    /** Preferencias presentes en el patch como mapa inmodificable, o vacío si no se envía. */
    @SuppressWarnings("unchecked")
    @Schema(hidden = true)
    public Map<String, Object> getPreferences() {
        Map<String, Object> preferences = (Map<String, Object>) fields.get("preferences");
        return preferences == null ? Map.of() : Collections.unmodifiableMap(preferences);
    }

    /** Valida que el displayName, cuando se envía, no sea nulo ni en blanco. */
    @AssertTrue(message = "El nombre no puede ser nulo ni vacío")
    @JsonIgnore
    public boolean isDisplayNameValid() {
        return !hasField("displayName") || (getDisplayName() != null && !getDisplayName().isBlank());
    }

    @Override
    public String toString() {
        return "UpdateUserRequest[datos personales omitidos]";
    }
}
