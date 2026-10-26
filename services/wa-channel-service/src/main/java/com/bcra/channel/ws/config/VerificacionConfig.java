package com.bcra.channel.ws.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bcra.channel.ws.vinculo.service.VerificadorIdentidad;

/** Rechaza la identidad hasta instalar una estrategia aprobada por Compliance. */
@Configuration
public class VerificacionConfig {

    @Bean
    @ConditionalOnMissingBean(VerificadorIdentidad.class)
    VerificadorIdentidad verificadorIdentidad() {
        return (tipo, numero, datos) -> false;
    }
}
