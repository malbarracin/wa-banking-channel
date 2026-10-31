package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resumen de una interacción, sin motivos libres ni datos de verificación; fecha en UTC. */
@Schema(description = "Evento resumido de auditoría; no incluye motivos libres ni datos de verificación")
public record EventoHistorialResponse(
        @Schema(description = "Acción auditada", example = "VERIFICACION_EXITOSA") AuditAccion accion,
        @Schema(description = "Resultado de la acción", example = "EXITOSO") AuditResultado resultado,
        @Schema(description = "Instante UTC del evento", example = "2026-10-26T12:00:00Z") Instant fecha) {
}
