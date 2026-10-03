package com.bank.users;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Base ejecutable del Servicio de Usuarios")
class UsersServiceApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Health devuelve UP con la base H2 disponible")
    void healthIsUp() {
        // Given: la aplicación arranca con perfil test y servidor HTTP real.
        // When
        var response = http.getForEntity("/actuator/health", String.class);
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("Info publica la identidad del servicio")
    void infoIdentifiesService() {
        // Given: el endpoint info está expuesto en la configuración base.
        // When
        var response = http.getForEntity("/actuator/info", com.fasterxml.jackson.databind.JsonNode.class);
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("app").path("name").asText()).isEqualTo("wa-users-service");
    }

    @Test
    @DisplayName("El datasource ejecuta SQL en H2 en memoria y JPA usa create-drop")
    void testDatabaseIsAvailable() throws Exception {
        // Given
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // When
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                // Then
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
                assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
            }
        }
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("La consola H2 y el endpoint env no están expuestos")
    void administrativeEndpointsAreNotExposed() {
        // Given: se conserva la exposición restringida de la configuración base.
        // When / Then
        assertThat(http.getForEntity("/h2-console/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
