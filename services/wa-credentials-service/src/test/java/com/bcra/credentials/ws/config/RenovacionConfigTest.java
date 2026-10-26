package com.bcra.credentials.ws.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class RenovacionConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RenovacionConfig.class);

    @Test
    @DisplayName("La configuración permite deshabilitar el planificador")
    void deshabilitaPlanificador() {
        contextRunner.withPropertyValues("credencial.renovacion.habilitada=false").run(context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("El planificador está habilitado por defecto")
    void habilitaPlanificadorPorDefecto() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
