package com.bcra.credentials.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CredentialsApplicationTest {

    @Test
    @DisplayName("El contexto arranca con la base H2 del perfil test")
    void cargaElContexto(@Autowired ApplicationContext context) {
        assertThat(context.getBean(CredentialsApplication.class)).isNotNull();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("test");
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }
}
