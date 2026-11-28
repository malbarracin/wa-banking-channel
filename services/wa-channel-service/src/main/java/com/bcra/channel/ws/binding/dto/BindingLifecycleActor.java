package com.bcra.channel.ws.binding.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quién dispara el bloqueo o la desvinculación del canal.
 */
@Schema(description = "Actor del bloqueo o desvinculación")
public enum BindingLifecycleActor {

    /** Cliente del canal, con confirmación explícita. */
    CLIENTE,

    /** Política o riesgo del banco; exige rol interno bank-policy. */
    BANCO
}
