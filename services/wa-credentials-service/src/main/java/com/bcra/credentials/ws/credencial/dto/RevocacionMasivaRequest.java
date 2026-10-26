package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/** Alcance de la revocación: TODAS sin segmento o POR_CLIENTE con identidad documental completa. */
@Schema(description = "Alcance de la revocación: TODAS sin segmento o POR_CLIENTE con identidad documental completa.")
public record RevocacionMasivaRequest(
        @Schema(description = "TODAS revoca todas las activas sin segmento; POR_CLIENTE requiere segmento",
                example = "POR_CLIENTE", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"TODAS", "POR_CLIENTE"})
        @NotBlank @Pattern(regexp = "TODAS|POR_CLIENTE") String criterio,
        @Schema(description = "Identidad documental obligatoria para POR_CLIENTE; omitir para TODAS",
                example = "{\"documentoTipo\":\"DNI\",\"documentoNumero\":\"12345678\"}")
        @Valid Segmento segmento) {

    /** @return si el segmento corresponde al criterio y evita ampliar accidentalmente el alcance. */
    @AssertTrue(message = "POR_CLIENTE requiere segmento; TODAS no admite segmento")
    @JsonIgnore
    public boolean isAlcanceValido() {
        return "POR_CLIENTE".equals(criterio) ? segmento != null : segmento == null;
    }

    /** Identidad del cliente en H1; alcanza todos sus números de WhatsApp. */
    @Schema(description = "Identidad documental del cliente; alcanza todos sus números")
    public record Segmento(
            @Schema(description = "Tipo documental del cliente en H1",
                    example = "DNI", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 30)
            @NotBlank @Size(max = 30) String documentoTipo,
            @Schema(description = "Número documental del cliente en H1",
                    example = "12345678", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
            @NotBlank @Size(max = 50) String documentoNumero) {
    }
}
