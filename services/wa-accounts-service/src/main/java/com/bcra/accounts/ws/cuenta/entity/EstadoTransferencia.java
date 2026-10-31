package com.bcra.accounts.ws.cuenta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estados del ciclo de una transferencia. */
@Schema(description = "PREPARADA: espera confirmación; CONFIRMADA: espera segunda confirmación; "
        + "EJECUTADA: fondos transferidos; RECHAZADA y EXPIRADA: estados del modelo sin confirmación posible")
public enum EstadoTransferencia {
    PREPARADA, CONFIRMADA, EJECUTADA, RECHAZADA, EXPIRADA
}

