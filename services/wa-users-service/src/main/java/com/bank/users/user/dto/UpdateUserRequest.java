package com.bank.users.user.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Actualización parcial de los datos permitidos de un cliente ({@code PATCH /api/v1/users/{id}}).
 *
 * <p>Solo {@code displayName} y {@code email} son modificables (OQ-3). Los campos omitidos o enviados en
 * {@code null} conservan su valor actual; al menos uno de los dos debe informarse. Cualquier otro campo
 * del JSON (datos legales, estado, preferencias, etc.) se rechaza al deserializar con 400
 * {@code BAD_REQUEST} «campo no modificable por este servicio», sin aplicar cambios parciales.</p>
 *
 * <p>{@code toString()} no incluye los valores (PII).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class UpdateUserRequest {

    private static final List<Object> ALLOWED_FIELDS = List.of("displayName", "email");

    /** Nuevo nombre de tratamiento (opcional). */
    private String displayName;

    /** Nuevo correo electrónico (opcional; con formato válido si se informa). */
    @Email
    private String email;

    /**
     * Regla de payload: al menos uno de {@code displayName} / {@code email} debe venir informado.
     *
     * @return {@code true} si hay algún campo a modificar
     */
    @JsonIgnore
    @AssertTrue(message = "debe informar al menos uno de displayName o email")
    public boolean isAnyFieldPresent() {
        return displayName != null || email != null;
    }

    /**
     * Rechaza cualquier propiedad JSON distinta de los campos permitidos. Se traduce a 400
     * {@code BAD_REQUEST} por el manejador global; el valor enviado nunca se expone.
     *
     * @param name  nombre de la propiedad no permitida
     * @param value valor enviado (ignorado)
     * @throws UnrecognizedPropertyException siempre
     */
    @JsonAnySetter
    void rejectNonModifiableField(String name, Object value) throws UnrecognizedPropertyException {
        throw new UnrecognizedPropertyException(null, "Non-modifiable field: " + name, null,
                UpdateUserRequest.class, name, ALLOWED_FIELDS);
    }
}
