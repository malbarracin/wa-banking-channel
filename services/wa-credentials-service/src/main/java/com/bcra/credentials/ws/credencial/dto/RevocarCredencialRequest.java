package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.constraints.NotNull;

import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;

/** Solicitud interna; H3 anterior omite el origen, que se interpreta como CANAL. */
public record RevocarCredencialRequest(@NotNull MotivoRevocacion motivo, RevocadaPor solicitadoPor) {

    public RevocarCredencialRequest {
        solicitadoPor = solicitadoPor == null ? RevocadaPor.CANAL : solicitadoPor;
    }
}
