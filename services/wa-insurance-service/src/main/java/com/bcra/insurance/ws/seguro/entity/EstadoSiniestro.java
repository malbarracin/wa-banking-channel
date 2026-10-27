package com.bcra.insurance.ws.seguro.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estados informados por el sistema de siniestros; RESUELTO permite una nueva denuncia. */
@Schema(description = "Estado del caso según el sistema de siniestros. RECIBIDO, EN_ANALISIS y DOCUMENTACION_PENDIENTE bloquean duplicados; RESUELTO permite una nueva denuncia.",
        allowableValues = {"RECIBIDO", "EN_ANALISIS", "DOCUMENTACION_PENDIENTE", "RESUELTO"})
public enum EstadoSiniestro {
    RECIBIDO, EN_ANALISIS, DOCUMENTACION_PENDIENTE, RESUELTO
}
