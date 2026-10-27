package com.bcra.accounts.ws.cuenta.dto;

import java.util.UUID;

/**
 * Destinatario activo agendado por el titular autenticado.
 * @param id identificador para seleccionar el destinatario de una transferencia
 * @param alias nombre del destinatario en la agenda
 * @param numeroCuentaDestino cuenta bancaria del destinatario
 * @param bancoDestino banco del destinatario; puede estar ausente
 */
public record TerceroResponse(UUID id, String alias, String numeroCuentaDestino, String bancoDestino) {

    @Override
    public String toString() {
        return "TerceroResponse[id=" + id + "]";
    }
}
