package com.bcra.users.ws.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import com.bcra.users.ws.user.exception.NonEditableFieldException;

/** Cambios permitidos. Los campos ausentes o null conservan el valor; preferencias reemplaza el texto completo. */
public record UpdateUserRequest(String email, String telefono, String nombreTratamiento, String preferencias) {

    /** Rechaza cualquier propiedad fuera del contrato, incluso si Jackson ignora desconocidas globalmente. */
    @JsonAnySetter
    public void rechazarCampo(String campo, JsonNode valor) {
        throw new NonEditableFieldException();
    }
}
