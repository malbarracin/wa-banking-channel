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

import com.bank.users.user.exception.InvalidUserUpdateException;
import com.bank.users.user.exception.NonUpdatableFieldException;

/**
 * Patch parcial: la presencia de una clave distingue omisión de eliminación explícita con null.
 * Las preferencias reemplazan el mapa completo; null lo vacía. No se valida un catálogo de claves.
 */
public record UpdateUserRequest(Map<String, Object> fields) {

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

    public boolean hasField(String field) {
        return fields.containsKey(field);
    }

    @Size(max = 120)
    public String getDisplayName() {
        return (String) fields.get("displayName");
    }

    @Email
    @Size(max = 254)
    @Pattern(regexp = "(?s).*\\S.*")
    public String getEmail() {
        return (String) fields.get("email");
    }

    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}[0-9]")
    public String getPhone() {
        return (String) fields.get("phone");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPreferences() {
        Map<String, Object> preferences = (Map<String, Object>) fields.get("preferences");
        return preferences == null ? Map.of() : Collections.unmodifiableMap(preferences);
    }

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
