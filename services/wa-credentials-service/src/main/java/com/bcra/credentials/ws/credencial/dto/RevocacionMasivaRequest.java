package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Alcance de la revocación: TODAS sin segmento o POR_CLIENTE con identidad documental completa. */
public record RevocacionMasivaRequest(
        @NotBlank @Pattern(regexp = "TODAS|POR_CLIENTE") String criterio,
        @Valid Segmento segmento) {

    /** @return si el segmento corresponde al criterio y evita ampliar accidentalmente el alcance. */
    @AssertTrue(message = "POR_CLIENTE requiere segmento; TODAS no admite segmento")
    @JsonIgnore
    public boolean isAlcanceValido() {
        return "POR_CLIENTE".equals(criterio) ? segmento != null : segmento == null;
    }

    /** Identidad del cliente en H1; alcanza todos sus números de WhatsApp. */
    public record Segmento(
            @NotBlank @Size(max = 30) String documentoTipo,
            @NotBlank @Size(max = 50) String documentoNumero) {
    }
}
