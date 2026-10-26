package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Confirmación del canal H3; ambos indicadores deben ser verdaderos para autorizar la emisión. */
public record EmitirCredencialRequest(
        @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String numeroWhatsapp,
        @NotBlank @Size(max = 30) String documentoTipo,
        @NotBlank @Size(max = 50) String documentoNumero,
        @NotNull Boolean vinculoActivo,
        @NotNull Boolean identidadVerificada) {

    @Override
    public String toString() {
        return "EmitirCredencialRequest[protegido]";
    }
}
