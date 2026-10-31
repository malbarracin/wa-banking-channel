package com.bcra.insurance.ws;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapConfigurationTest {

    @Test
    @DisplayName("La configuración de producción usa el puerto y la base de datos de seguros")
    void configuraLosValoresPredeterminadosDeSeguros() throws IOException {
        MockEnvironment environment = cargarConfiguracionDeProduccion();

        assertThat(environment.getProperty("server.port")).isEqualTo("8085");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/wa_insurance");
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("wa-insurance-service");
    }

    @Test
    @DisplayName("El despliegue puede reemplazar puerto, conexión y credenciales mediante el entorno")
    void permiteConfigurarElDespliegueDesdeElEntorno() throws IOException {
        MockEnvironment environment = cargarConfiguracionDeProduccion()
                .withProperty("PORT", "9085")
                .withProperty("DB_URL", "jdbc:postgresql://insurance-db:5432/seguros")
                .withProperty("DB_USERNAME", "insurance_app")
                .withProperty("DB_PASSWORD", "clave-de-prueba");

        assertThat(environment.getProperty("server.port")).isEqualTo("9085");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://insurance-db:5432/seguros");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("insurance_app");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("clave-de-prueba");
    }

    private MockEnvironment cargarConfiguracionDeProduccion() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        var resource = new FileSystemResource("src/main/resources/application.yml");
        var propertySources = new YamlPropertySourceLoader().load("production", resource);
        propertySources.forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
