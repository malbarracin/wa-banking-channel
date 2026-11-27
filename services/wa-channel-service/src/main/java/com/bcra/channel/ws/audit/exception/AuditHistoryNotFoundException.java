package com.bcra.channel.ws.audit.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/**
 * No hay entradas de auditoría para el número consultado.
 */
public class AuditHistoryNotFoundException extends NotFoundException {

    public AuditHistoryNotFoundException() {
        super("No hay historial de auditoría para el número indicado");
    }
}
