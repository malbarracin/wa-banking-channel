package com.bank.users.user.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Cambios parciales: los campos omitidos se conservan y los null explícitos se rechazan.
 * Preferencias reemplaza el mapa completo; solo debe contener preferencias generales no sensibles.
 */
public record UpdateUserRequest(
        @Size(max = 255)
        @Pattern(regexp = "(?s).*\\S.*") String nombreTratamiento,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String telefonoContacto,
        Map<String, Object> preferencias) {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "nombreTratamiento", "email", "telefonoContacto", "preferencias");

    public UpdateUserRequest {
        if (preferencias != null) {
            preferencias = Map.copyOf(preferencias);
        }
    }

    /** Exige al menos un campo permitido antes de ejecutar el caso de uso. */
    @JsonIgnore
    @AssertTrue
    public boolean isCambioPresente() {
        return nombreTratamiento != null || email != null || telefonoContacto != null || preferencias != null;
    }

    /** Distingue campos omitidos de null explícito y rechaza tipos o campos ajenos al contrato. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static UpdateUserRequest fromJson(Map<String, Object> fields) {
        if (!ALLOWED_FIELDS.containsAll(fields.keySet()) || fields.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La solicitud contiene campos no permitidos o valores null");
        }
        Map<String, Object> preferences = null;
        if (fields.containsKey("preferencias")) {
            if (!(fields.get("preferencias") instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("Las preferencias deben ser un objeto");
            }
            preferences = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                preferences.put((String) entry.getKey(), entry.getValue());
            }
        }
        return new UpdateUserRequest(text(fields, "nombreTratamiento"), text(fields, "email"),
                text(fields, "telefonoContacto"), preferences);
    }

    private static String text(Map<String, Object> fields, String field) {
        Object value = fields.get(field);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("El campo debe ser texto");
        }
        return (String) value;
    }
}
