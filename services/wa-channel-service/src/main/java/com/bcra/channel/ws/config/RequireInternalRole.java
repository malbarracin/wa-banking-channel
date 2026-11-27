package com.bcra.channel.ws.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige el header {@code X-Internal-Role} con el valor configurado para el rol indicado.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInternalRole {

    /**
     * Rol interno esperado (mapeado a {@code bank.channel.internal-roles.*}).
     */
    InternalRole value();

    /**
     * Roles internos del piloto configurables en {@code bank.channel.internal-roles}.
     */
    enum InternalRole {
        /** Historial y consultas de soporte. */
        SUPPORT,
        /** Bloqueo / desvinculación por política del banco. */
        BANK_POLICY
    }
}
