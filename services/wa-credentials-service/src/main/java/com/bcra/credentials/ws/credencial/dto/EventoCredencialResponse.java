package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;

/** Evento resumido: motivo protegido en origen, sin token ni identificador de credencial. */
public record EventoCredencialResponse(AuditAccion accion, AuditResultado resultado, Instant fecha, String motivo) {
}
