package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/** Resultado del proceso para H0.2; no constituye una credencial de sesión de H2. */
public record VerificacionResultadoResponse(boolean exitosa, UUID vinculoId, String numeroWhatsapp,
        String documentoTipo, String documentoNumero, VinculoEstado estadoVinculo,
        Integer intentosRestantes, Instant verificadoEn) {

    @Override
    public String toString() {
        return "VerificacionResultadoResponse[exitosa=" + exitosa + "]";
    }
}
