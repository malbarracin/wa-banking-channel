package com.wa.banking.accounts.api.v1.controller;

import com.wa.banking.accounts.api.error.ErrorResponse;
import com.wa.banking.accounts.api.v1.dto.AccountBalanceResponse;
import com.wa.banking.accounts.api.v1.dto.AccountListResponse;
import com.wa.banking.accounts.config.SecurityFilterConfig;
import com.wa.banking.accounts.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para operaciones F1 de cuentas del titular autenticado.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts V1", description = "Consulta de cuentas y saldos del titular autenticado (H4 piloto F1)")
@SecurityRequirement(name = "sessionCredential")
public class AccountControllerV1 {

    private final AccountService accountService;

    @GetMapping
    @Operation(
            summary = "Listar cuentas del titular autenticado",
            description = "Devuelve las cuentas activas del titular asociado a la credencial H2 validada. "
                    + "Requiere headers Authorization (Bearer) y X-Credential-Id."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Listado de cuentas del titular",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountListResponse.class),
                            examples = @ExampleObject(
                                    name = "Cuentas del titular demo",
                                    value = """
                                            {
                                              "accounts": [
                                                {
                                                  "id": "674a1b2c3d4e5f6789012345",
                                                  "alias": "Cuenta Sueldo",
                                                  "type": "CHECKING",
                                                  "currency": "ARS",
                                                  "availableBalance": 125000.50,
                                                  "ledgerBalance": 125000.50,
                                                  "status": "ACTIVE"
                                                },
                                                {
                                                  "id": "674a1b2c3d4e5f6789012346",
                                                  "alias": "Caja de Ahorro USD",
                                                  "type": "SAVINGS",
                                                  "currency": "USD",
                                                  "availableBalance": 1500.00,
                                                  "ledgerBalance": 1500.00,
                                                  "status": "ACTIVE"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credencial ausente, inválida o expirada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Sin credencial válida",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Por seguridad, verificá tu identidad para ver tus cuentas.",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AccountListResponse> listAccounts(
            @RequestAttribute(SecurityFilterConfig.BANK_USER_ID_ATTRIBUTE) String bankUserId) {
        return ResponseEntity.ok(accountService.listAccountsByBankUserId(bankUserId));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(
            summary = "Consultar saldo disponible y contable de una cuenta",
            description = "Devuelve saldo disponible y contable de la cuenta indicada. "
                    + "Solo se permiten cuentas del titular autenticado; cuentas ajenas responden 404."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Saldo de la cuenta solicitada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountBalanceResponse.class),
                            examples = @ExampleObject(
                                    name = "Saldo Cuenta Sueldo",
                                    value = """
                                            {
                                              "accountId": "674a1b2c3d4e5f6789012345",
                                              "alias": "Cuenta Sueldo",
                                              "currency": "ARS",
                                              "availableBalance": 125000.50,
                                              "ledgerBalance": 125000.50
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credencial ausente, inválida o expirada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Sin credencial válida",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Por seguridad, verificá tu identidad para ver tus cuentas.",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cuenta inexistente o no pertenece al titular autenticado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Cuenta no encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Account not found: missing-account-id",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @RequestAttribute(SecurityFilterConfig.BANK_USER_ID_ATTRIBUTE) String bankUserId,
            @Parameter(description = "Identificador de la cuenta (campo id del listado)", example = "674a1b2c3d4e5f6789012345")
            @PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getBalance(bankUserId, accountId));
    }
}
