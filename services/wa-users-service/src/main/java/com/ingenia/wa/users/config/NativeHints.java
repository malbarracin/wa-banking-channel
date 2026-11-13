package com.ingenia.wa.users.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Runtime hints para imagen nativa GraalVM (scaffold).
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Configuration
@ImportRuntimeHints(NativeHints.NativeHintsRegistrar.class)
public class NativeHints {

    /**
     * Registra recursos y tipos base usados en AOT.
     */
    public static class NativeHintsRegistrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources()
                    .registerPattern("application*.yml")
                    .registerPattern("META-INF/spring/*");
        }
    }
}
