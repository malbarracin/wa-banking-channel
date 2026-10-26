package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;

/** Resumen de una interacción, sin motivos libres ni datos de verificación; fecha en UTC. */
public record EventoHistorialResponse(AuditAccion accion, AuditResultado resultado, Instant fecha) {
}
