package com.bcra.accounts.ws;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BootstrapIntegrationTest {

    private final MockMvc mockMvc;
    private final DataSource dataSource;

    @Autowired
    BootstrapIntegrationTest(MockMvc mockMvc, DataSource dataSource) {
        this.mockMvc = mockMvc;
        this.dataSource = dataSource;
    }

    @Test
    @DisplayName("El endpoint de salud informa que el servicio y su base están disponibles")
    void informaServicioDisponible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("La configuración de test permite ejecutar SQL en H2 sin PostgreSQL externo")
    void ejecutaSqlEnBaseDePruebas() throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var filas = statement.executeQuery("SELECT 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(filas.next()).isTrue();
            assertThat(filas.getInt(1)).isEqualTo(1);
        }
    }
}
