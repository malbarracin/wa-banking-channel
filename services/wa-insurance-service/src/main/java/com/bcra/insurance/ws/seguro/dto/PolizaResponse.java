package com.bcra.insurance.ws.seguro.dto;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Datos de una póliza autorizada; id permite seleccionar sus coberturas y el identificador corto presentarla. */
@Schema(description = "Póliza vigente autorizada para el cliente autenticado")
public record PolizaResponse(
        @Schema(description = "Identificador de la póliza", example = "e7a62f43-9b82-4c01-b2ab-7f902eab1234")
        UUID id,
        @Schema(description = "Número de póliza", example = "POL-000123") String numeroPoliza,
        @Schema(description = "Ramo", example = "AUTO") String ramo,
        @Schema(description = "Indica si la póliza está vigente", example = "true") boolean vigente,
        @Schema(description = "Inicio de vigencia (ISO-8601)", example = "2026-01-01") LocalDate fechaVigenciaDesde,
        @Schema(description = "Fin de vigencia (ISO-8601)", example = "2027-01-01") LocalDate fechaVigenciaHasta,
        @Schema(description = "Últimos caracteres del número para presentación corta", example = "0123")
        String identificadorCorto) {
}
