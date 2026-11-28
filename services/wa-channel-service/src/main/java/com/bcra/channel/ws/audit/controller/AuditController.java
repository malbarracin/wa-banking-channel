package com.bcra.channel.ws.audit.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.service.AuditService;
import com.bcra.channel.ws.config.RequireInternalRole;
import com.bcra.channel.ws.config.RequireInternalRole.InternalRole;
import com.bcra.channel.ws.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Historial resumido de auditoría para soporte del banco.
 */
@RestController
@RequestMapping("/api/v1/bindings")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Historial resumido del vínculo para soporte")
public class AuditController {

    private final AuditService auditService;

    /**
     * Devuelve el historial de auditoría de un número, ordenado por fecha descendente.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @return entradas de auditoría
     */
    @GetMapping(path = "/{whatsappNumber}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @RequireInternalRole(InternalRole.SUPPORT)
    @Operation(
            summary = "Historial de auditoría para soporte",
            description = "Requiere header X-Internal-Role con el valor de bank.channel.internal-roles.support. "
                    + "Las entradas no incluyen secretos ni datos de verificación.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Historial ordenado por fecha descendente",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = AuditEntryResponse.class)),
                            examples = @ExampleObject(value = """
                                    [{
                                      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                      "whatsappNumber": "+5491112345678",
                                      "action": "TERMS_ACCEPTED",
                                      "result": "OK",
                                      "actor": "CLIENTE",
                                      "detail": "Términos aceptados",
                                      "occurredAt": "2026-11-27T15:30:00Z"
                                    }]
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Header X-Internal-Role ausente",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Rol interno inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Número sin entradas de auditoría",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    @Parameters({
            @Parameter(
                    name = "whatsappNumber",
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true,
                    in = ParameterIn.PATH),
            @Parameter(
                    name = "X-Internal-Role",
                    description = "Rol interno; debe coincidir con bank.channel.internal-roles.support",
                    example = "support",
                    required = true,
                    in = ParameterIn.HEADER)
    })
    public List<AuditEntryResponse> history(@PathVariable String whatsappNumber) {
        return auditService.findHistoryByWhatsappNumber(whatsappNumber);
    }
}
