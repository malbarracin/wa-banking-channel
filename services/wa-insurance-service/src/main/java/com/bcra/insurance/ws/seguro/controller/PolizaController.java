package com.bcra.insurance.ws.seguro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.exception.ErrorResponse;
import com.bcra.insurance.ws.seguro.dto.CoberturaResponse;
import com.bcra.insurance.ws.seguro.dto.PolizaResponse;
import com.bcra.insurance.ws.seguro.service.CoberturaService;
import com.bcra.insurance.ws.seguro.service.PolizaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Consultas de seguros con credencial de sesión de H2. */
@Tag(name = "Seguros", description = "Consulta de pólizas vigentes y coberturas")
@RestController
@RequestMapping("/api/v1/seguros")
@RequiredArgsConstructor
public class PolizaController {

    private final PolizaService polizas;
    private final CoberturaService coberturas;

    /**
     * Listado de pólizas vigentes autorizadas para el titular validado por H2.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @return listado de pólizas vigentes autorizadas
     */
    @Operation(summary = "Listar pólizas vigentes", description = "Lista las pólizas vigentes donde el cliente es titular o asegurado, validando la credencial contra H2.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de pólizas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PolizaResponse.class)),
                            examples = @ExampleObject(value = """
                                    [
                                      {
                                        "id": "e7a62f43-9b82-4c01-b2ab-7f902eab1234",
                                        "numeroPoliza": "POL-000123",
                                        "ramo": "AUTO",
                                        "vigente": true,
                                        "fechaVigenciaDesde": "2026-01-01",
                                        "fechaVigenciaHasta": "2027-01-01",
                                        "identificadorCorto": "0123"
                                      }
                                    ]
                                    """))),
            @ApiResponse(responseCode = "401", description = "Credencial inválida o ausente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "403", description = "Sin autorización sobre el recurso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Forbidden")))
    })
    @GetMapping
    public List<PolizaResponse> listar(
            @Parameter(description = "JWT de sesión validado por H2", required = true, example = "eyJhbGciOi...")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial) {
        return polizas.listar(credencial);
    }

    /**
     * Resumen de coberturas y exclusiones para una póliza autorizada.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param polizaId identificador de la póliza
     * @return resumen de condiciones
     */
    @Operation(summary = "Consultar coberturas", description = "Muestra coberturas, exclusiones y resumen en lenguaje simple para una póliza del titular.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coberturas de la póliza",
                    content = @Content(schema = @Schema(implementation = CoberturaResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "coberturas": ["Responsabilidad civil", "Daños propios"],
                                      "exclusiones": ["Uso comercial no declarado"],
                                      "resumen": true,
                                      "notaPrevalencia": "Este es un resumen informativo. En caso de disputa prevalecen las condiciones generales oficiales de la póliza."
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Credencial inválida o ausente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "403", description = "No autorizado para esta póliza",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Forbidden"))),
            @ApiResponse(responseCode = "404", description = "Póliza no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound")))
    })
    @GetMapping("/{polizaId}/coberturas")
    public CoberturaResponse consultarCoberturas(
            @Parameter(description = "JWT de sesión validado por H2", required = true, example = "eyJhbGciOi...")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Parameter(description = "Identificador de la póliza", required = true, example = "e7a62f43-9b82-4c01-b2ab-7f902eab1234")
            @PathVariable UUID polizaId) {
        return coberturas.consultar(credencial, polizaId);
    }
}
