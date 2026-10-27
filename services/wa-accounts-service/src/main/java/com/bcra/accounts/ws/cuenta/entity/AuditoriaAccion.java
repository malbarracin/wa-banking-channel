package com.bcra.accounts.ws.cuenta.entity;

/** Operaciones auditables del servicio de cuentas. */
public enum AuditoriaAccion {
    LISTAR_CUENTAS, CONSULTAR_SALDO, LISTAR_MOVIMIENTOS, PREPARAR_TRANSFERENCIA,
    CONFIRMAR_TRANSFERENCIA, EJECUTAR_TRANSFERENCIA, RECHAZAR_TRANSFERENCIA
}

