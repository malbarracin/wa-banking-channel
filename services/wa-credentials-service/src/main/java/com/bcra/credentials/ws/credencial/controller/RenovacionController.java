package com.bcra.credentials.ws.credencial.controller;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.service.RenovacionService;
import com.bcra.credentials.ws.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Entrega y confirmación de reemplazos al poseedor de la credencial del canal H3. */
@RestController
@RequestMapping("/api/v1/credenciales/renovacion")
@RequiredArgsConstructor
@Tag(name = "Credenciales")
public class RenovacionController {

    private final RenovacionService service;

    /** @return el mismo reemplazo en cada reintento; el JWT anterior sigue activo. */
    @Operation(summary = "Obtener renovación preparada",
            description = "H3 presenta el JWT vigente y guarda durablemente el reemplazo antes de confirmarlo.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ValidarCredencialRequest.class),
                            examples = @ExampleObject(name = "Actual", value = "{\"credencial\":\"JWT_ACTUAL\"}"))))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JWT sustituto; Cache-Control: no-store",
                content = @Content(schema = @Schema(implementation = EmitirCredencialResponse.class),
                        examples = @ExampleObject(name = "Reemplazo", value = """
                                {"credencial":"JWT_NUEVO","fechaExpiracion":"2026-11-01T12:00:00Z"}
                                """))),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o BAD_REQUEST",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "ValidationError",
                                ref = "#/components/examples/ValidationError"))),
        @ApiResponse(responseCode = "401", description = "Credencial inválida, revocada, expirada o con riesgo",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized"))),
        @ApiResponse(responseCode = "404", description = "Todavía no hay renovación preparada",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NoPreparada", value = """
                                {"code":"NOT_FOUND","message":"Todavía no hay una renovación preparada",
                                 "details":[],"timestamp":"2026-10-26T12:00:00Z"}
                                """))),
        @ApiResponse(responseCode = "409", description = "Conflicto de persistencia",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    @PostMapping("/obtener")
    public ResponseEntity<EmitirCredencialResponse> obtener(@Valid @RequestBody ValidarCredencialRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtener(request.credencial()));
    }

    /** Confirma con el JWT nuevo después de guardarlo; permite repetir una confirmación perdida. */
    @Operation(summary = "Confirmar recepción durable de renovación",
            description = "H3 presenta el JWT nuevo. Activa el reemplazo y revoca el anterior atómicamente.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ValidarCredencialRequest.class),
                            examples = @ExampleObject(name = "Nuevo", value = "{\"credencial\":\"JWT_NUEVO\"}"))))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Reemplazo activo, también ante repetición"),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o BAD_REQUEST",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "ValidationError",
                                ref = "#/components/examples/ValidationError"))),
        @ApiResponse(responseCode = "401", description = "Reemplazo o credencial anterior no autorizados",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized"))),
        @ApiResponse(responseCode = "409", description = "Conflicto de persistencia",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmar(@Valid @RequestBody ValidarCredencialRequest request) {
        service.confirmar(request.credencial());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
