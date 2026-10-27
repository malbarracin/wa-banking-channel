package com.bcra.insurance.ws.seguro.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;

/**
 * Captura mínima confirmada de una denuncia (póliza, tipo, fecha y descripción).
 *
 * @param polizaId póliza autorizada afectada
 * @param tipoSiniestro tipo de evento
 * @param fechaSiniestro fecha del hecho
 * @param descripcion relato breve sin datos médicos sensibles
 */
public record DenunciaRequest(
        @NotNull UUID polizaId,
        @NotNull TipoSiniestro tipoSiniestro,
        @NotNull LocalDate fechaSiniestro,
        @NotBlank String descripcion) {

    @Override
    public String toString() {
        return "DenunciaRequest[polizaId=" + polizaId + ", tipoSiniestro=" + tipoSiniestro
                + ", fechaSiniestro=" + fechaSiniestro + "]";
    }
}
