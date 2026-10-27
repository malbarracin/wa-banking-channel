package com.bcra.accounts.ws.cuenta.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Destinatario activo agendado por el titular autenticado.
 * @param id identificador para seleccionar el destinatario de una transferencia
 * @param alias nombre del destinatario en la agenda
 * @param numeroCuentaDestino cuenta bancaria del destinatario
 * @param bancoDestino banco del destinatario; puede estar ausente
 */
public record TerceroResponse(
        @Schema(description = "Identificador del destinatario agendado",
                example = "11111111-1111-4111-8111-111111111111")
        UUID id,
        @Schema(description = "Alias de la cuenta o destinatario",
                example = "ahorros")
        String alias,
        @Schema(description = "Cuenta del destinatario",
                example = "003")
        String numeroCuentaDestino,
        @Schema(description = "Banco del destinatario; puede estar ausente",
                example = "Banco local")
        String bancoDestino) {

    @Override
    public String toString() {
        return "TerceroResponse[id=" + id + "]";
    }
}
