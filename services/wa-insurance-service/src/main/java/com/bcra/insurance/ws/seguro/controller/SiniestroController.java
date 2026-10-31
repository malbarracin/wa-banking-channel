package com.bcra.insurance.ws.seguro.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.exception.ErrorResponse;
import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.dto.DocumentacionPendienteResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.service.DocumentacionService;
import com.bcra.insurance.ws.seguro.service.SiniestroService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Denuncia y seguimiento de siniestros con credencial de sesión de H2. */
@Tag(name = "Siniestros", description = "Denuncia, seguimiento y documentación pendiente")
@RestController
@RequestMapping("/api/v1/siniestros")
@RequiredArgsConstructor
public class SiniestroController {

    private final SiniestroService siniestros;
    private final DocumentacionService documentacion;

    /**
     * Registra una denuncia confirmada tras validar los cuatro campos mínimos.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param request captura de póliza, tipo, fecha y descripción
     * @return caso abierto con número único y próximos pasos
     */
    @Operation(summary = "Denunciar siniestro", description = "Registra la denuncia tras validar póliza autorizada, duplicados y campos mínimos. Genera número de caso único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Caso abierto",
                    content = @Content(schema = @Schema(implementation = DenunciaResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "numeroCaso": "SIN-2026-000123",
                                      "proximosPasos": "Enviá la documentación requerida por los canales oficiales del banco.",
                                      "plazos": "Los plazos dependen del proceso formal correspondiente a tu ramo; no prometemos liquidación por este canal."
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ValidationError"))),
            @ApiResponse(responseCode = "401", description = "Credencial inválida o ausente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "403", description = "No autorizado sobre la póliza",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Forbidden"))),
            @ApiResponse(responseCode = "404", description = "Póliza no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "409", description = "Siniestro duplicado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Duplicate")))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Datos mínimos para la denuncia",
            content = @Content(schema = @Schema(implementation = DenunciaRequest.class),
                    examples = {
                            @ExampleObject(name = "AUTO", summary = "Siniestro de auto", value = """
                                    {
                                      "polizaId": "e7a62f43-9b82-4c01-b2ab-7f902eab1234",
                                      "tipoSiniestro": "AUTO",
                                      "fechaSiniestro": "2026-10-20",
                                      "descripcion": "Choque en intersección"
                                    }
                                    """),
                            @ExampleObject(name = "HOGAR", summary = "Siniestro de hogar", value = """
                                    {
                                      "polizaId": "e7a62f43-9b82-4c01-b2ab-7f902eab1234",
                                      "tipoSiniestro": "HOGAR",
                                      "fechaSiniestro": "2026-10-18",
                                      "descripcion": "Rotura de cañería"
                                    }
                                    """)
                    }))
    public DenunciaResponse denunciar(
            @Parameter(description = "JWT de sesión validado por H2", required = true, example = "eyJhbGciOi...")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody DenunciaRequest request) {
        return siniestros.denunciar(credencial, request);
    }

    /**
     * Refleja el estado del sistema de siniestros para un caso propio.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return estado y pendiente; 401/403/404 según credencial, titularidad o existencia
     */
    @Operation(summary = "Consultar siniestro", description = "Consulta el estado del caso y la acción pendiente del cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del caso",
                    content = @Content(schema = @Schema(implementation = SiniestroResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "numeroCaso": "SIN-2026-000123",
                                      "estado": "DOCUMENTACION_PENDIENTE",
                                      "pendiente": "Presentar informe pericial"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Credencial inválida o ausente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "403", description = "Caso no pertenece al titular",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Forbidden"))),
            @ApiResponse(responseCode = "404", description = "Caso no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound")))
    })
    @GetMapping("/{numeroCaso}")
    public SiniestroResponse consultar(
            @Parameter(description = "JWT de sesión validado por H2", required = true, example = "eyJhbGciOi...")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Parameter(description = "Identificador del caso", required = true, example = "SIN-2026-000123")
            @PathVariable String numeroCaso) {
        return siniestros.consultar(credencial, numeroCaso);
    }

    /**
     * Recordatorio de documentación pendiente con lista simple y orientación formal.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return documentos pendientes; 401/403/404 según credencial, titularidad o existencia
     */
    @Operation(summary = "Documentación pendiente", description = "Lista documentos pendientes y orientación de envío para el caso propio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documentación pendiente",
                    content = @Content(schema = @Schema(implementation = DocumentacionPendienteResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "documentos": ["Informe pericial", "Fotografías del siniestro"],
                                      "orientacionEnvio": "Enviá estos documentos por los canales oficiales del banco; este chat no reemplaza el proceso formal."
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Credencial inválida o ausente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "403", description = "Caso no pertenece al titular",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Forbidden"))),
            @ApiResponse(responseCode = "404", description = "Caso no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound")))
    })
    @GetMapping("/{numeroCaso}/documentacion-pendiente")
    public DocumentacionPendienteResponse documentacionPendiente(
            @Parameter(description = "JWT de sesión validado por H2", required = true, example = "eyJhbGciOi...")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Parameter(description = "Identificador del caso", required = true, example = "SIN-2026-000123")
            @PathVariable String numeroCaso) {
        return documentacion.listarPendiente(credencial, numeroCaso);
    }
}
