package com.wa.banking.channel.api.v1.dto;

import com.wa.banking.channel.entity.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entrada del historial resumido de interacciones para soporte.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entrada del historial de interacciones")
public class InteractionHistoryItemV1 {

    @Schema(description = "Identificador de la interacción")
    private String id;

    @Schema(description = "Tipo de interacción")
    private InteractionType type;

    @Schema(description = "Resultado de la interacción", example = "SUCCESS")
    private String result;

    @Schema(description = "Resumen sin datos sensibles")
    private String summary;

    @Schema(description = "Fecha de la interacción")
    private Instant occurredAt;
}
