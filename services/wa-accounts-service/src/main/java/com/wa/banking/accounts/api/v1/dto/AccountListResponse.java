package com.wa.banking.accounts.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO de respuesta con el listado de cuentas del titular autenticado.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Listado de cuentas del titular autenticado")
public record AccountListResponse(

        @Schema(description = "Cuentas del titular")
        List<AccountResponse> accounts
) {
}
