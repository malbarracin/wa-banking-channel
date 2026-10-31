package com.bcra.cards.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.location=file:src/main/resources/application.yml")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BootstrapConfigurationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("La configuración entregada arranca con el perfil test y conserva los valores del servicio")
    void cargaLaConfiguracionEntregada() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("wa-cards-service");
        assertThat(environment.getProperty("server.port", Integer.class))
                .isEqualTo(environment.getProperty("PORT", Integer.class, 8086));
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:wa_cards");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
    }

    @Test
    @DisplayName("Actuator publica health e info y comprueba la conexión a la base de prueba")
    void publicaElEstadoDelServicio() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una ruta inexistente usa el contrato de errores con el advice registrado por Spring")
    void respondeElContratoDesdeElContextoReal() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El recurso no existe"))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
