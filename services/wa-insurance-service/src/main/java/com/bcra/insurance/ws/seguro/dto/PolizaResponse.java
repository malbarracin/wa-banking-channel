package com.bcra.insurance.ws.seguro.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Datos de una póliza autorizada; id permite seleccionar sus coberturas y el identificador corto presentarla. */
public record PolizaResponse(UUID id, String numeroPoliza, String ramo, boolean vigente,
        LocalDate fechaVigenciaDesde, LocalDate fechaVigenciaHasta, String identificadorCorto) {
}
