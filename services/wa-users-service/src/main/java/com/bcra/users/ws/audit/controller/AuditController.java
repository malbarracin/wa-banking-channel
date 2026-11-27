package com.bcra.users.ws.audit.controller;

import java.util.List;
import java.util.UUID;

import com.bcra.users.ws.audit.dto.AuditEntryResponse;
import com.bcra.users.ws.audit.mapper.AuditMapper;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta el historial de auditoría de un cliente. */
@RestController
@RequestMapping("/api/v1/users/{id}/audit")
@Tag(name = "Auditoría", description = "Historial de altas, cambios de estado y modificaciones")
public class AuditController {

    private final AuditService service;
    private final AuditMapper mapper;

    public AuditController(AuditService service, AuditMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Devuelve las entradas ordenadas por fecha.
     *
     * @param id identificador del cliente
     * @return historial cronológico sin valores personales
     */
    @GetMapping
    @Operation(summary = "Consultar auditoría",
            description = "Devuelve el historial cronológico del cliente (fecha, acción y resultado).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = AuditEntryResponse.class)),
                            examples = @ExampleObject(value = """
                                    [{"id":"a1a2a3a4-1111-4444-8888-123456789abc",\
                                    "userId":"123e4567-e89b-12d3-a456-426614174000","action":"ALTA",\
                                    "result":"OK","previousStatus":null,"newStatus":"ACTIVE",\
                                    "changedFields":null,"actor":"operador-123",\
                                    "occurredAt":"2026-11-27T12:00:00Z"}]"""))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest",
                                    ref = "#/components/examples/BadRequest"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError",
                                    ref = "#/components/examples/InternalError")))})
    public List<AuditEntryResponse> history(
            @Parameter(description = "Identificador del cliente",
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        return service.history(id).stream().map(mapper::toResponse).toList();
    }
}
