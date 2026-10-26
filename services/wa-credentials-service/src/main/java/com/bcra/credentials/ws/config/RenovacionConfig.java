package com.bcra.credentials.ws.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa el planificador únicamente cuando la renovación automática está habilitada. */
@Configuration
@ConditionalOnProperty(name = "credencial.renovacion.habilitada", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class RenovacionConfig {
}
