package com.bcra.channel.ws;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelApplicationTest {

    @Test
    @DisplayName("El perfil test conecta con la base propia y conserva la configuración segura del servicio")
    void conectaConLaBaseDelCanal(@Autowired DataSource dataSource, @Autowired Environment environment)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:wa_channel_test");
        }

        assertThat(environment.getRequiredProperty("spring.application.name")).isEqualTo("wa-channel-service");
        assertThat(environment.getRequiredProperty("server.port", Integer.class))
                .isEqualTo(environment.getProperty("PORT", Integer.class, 8081));
        assertThat(environment.getRequiredProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getRequiredProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
        assertThat(environment.getRequiredProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getRequiredProperty("server.error.include-message")).isEqualTo("never");
    }

    @Test
    @DisplayName("El servicio arranca con la base H2 del perfil test")
    void cargaElContexto() {
        // El arranque verifica el wiring y la configuración JPA del scaffold.
    }

    @Test
    @DisplayName("Actuator informa que la aplicación y la base están disponibles")
    void informaSalud(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("El advice registrado responde el contrato para rutas inexistentes")
    void responde404ParaRutaInexistente(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(get("/api/v1/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
