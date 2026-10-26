package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;

/** Solicitud interna; H3 anterior omite el origen, que se interpreta como CANAL. */
@Schema(description = "Solicitud interna; H3 anterior omite el origen, que se interpreta como CANAL.")
public record RevocarCredencialRequest(
        @Schema(description = "Causa de revocación o motivo de auditoría sin secretos",
                example = "BLOQUEO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull MotivoRevocacion motivo,
        @Schema(description = "Origen de la revocación: CANAL, BANCO o SISTEMA; omitido equivale a CANAL",
                example = "CANAL", defaultValue = "CANAL")
        RevocadaPor solicitadoPor) {

    public RevocarCredencialRequest {
        solicitadoPor = solicitadoPor == null ? RevocadaPor.CANAL : solicitadoPor;
    }
}
