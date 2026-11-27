package com.bcra.users.ws.audit.controller;

import java.util.List;
import java.util.UUID;

import com.bcra.users.ws.audit.dto.AuditEntryResponse;
import com.bcra.users.ws.audit.mapper.AuditMapper;
import com.bcra.users.ws.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta el historial de auditoría de un cliente. */
@RestController
@RequestMapping("/api/v1/users/{id}/audit")
public class AuditController {
    private final AuditService service;
    private final AuditMapper mapper;

    public AuditController(AuditService service, AuditMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Devuelve las entradas ordenadas por fecha. */
    @GetMapping
    @Operation(summary = "Consultar auditoría", description = "Devuelve el historial cronológico del cliente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial", content = @Content(
                examples = @ExampleObject(value = "[]"))),
        @ApiResponse(responseCode = "400", description = "Identificador inválido", content = @Content(
                examples = @ExampleObject(value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\","
                        + "\"details\":[],\"timestamp\":\"2026-01-01T12:00:00Z\"}"))),
        @ApiResponse(responseCode = "500", description = "Error interno")
    })
    public List<AuditEntryResponse> history(@Parameter(description = "Identificador del cliente",
            example = "b1b2b3b4-1111-4444-8888-123456789abc") @PathVariable UUID id) {
        return service.history(id).stream().map(mapper::toResponse).toList();
    }
}
