package com.bcra.insurance.ws.seguro.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Captura mínima confirmada de una denuncia (póliza, tipo, fecha y descripción).
 *
 * @param polizaId póliza autorizada afectada
 * @param tipoSiniestro tipo de evento
 * @param fechaSiniestro fecha del hecho
 * @param descripcion relato breve sin datos médicos sensibles
 */
@Schema(description = "Datos mínimos confirmados para abrir una denuncia")
public record DenunciaRequest(
        @NotNull
        @Schema(description = "Identificador de la póliza afectada",
                example = "e7a62f43-9b82-4c01-b2ab-7f902eab1234", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID polizaId,
        @NotNull
        @Schema(description = "Tipo de siniestro", example = "AUTO", requiredMode = Schema.RequiredMode.REQUIRED)
        TipoSiniestro tipoSiniestro,
        @NotNull
        @Schema(description = "Fecha del hecho (ISO-8601)", example = "2026-10-20",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate fechaSiniestro,
        @NotBlank
        @Schema(description = "Descripción breve sin datos médicos sensibles", example = "Choque en intersección",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String descripcion) {

    @Override
    public String toString() {
        return "DenunciaRequest[polizaId=" + polizaId + ", tipoSiniestro=" + tipoSiniestro
                + ", fechaSiniestro=" + fechaSiniestro + "]";
    }
}
