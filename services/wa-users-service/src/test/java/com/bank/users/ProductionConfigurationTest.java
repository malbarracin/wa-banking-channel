package com.bank.users;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Configuración base de producción")
class ProductionConfigurationTest {

    @Test
    @DisplayName("El datasource resuelve las tres variables externas sin valores embebidos")
    void datasourceUsesExternalVariables() throws IOException {
        var environment = baseEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("external", Map.of(
                "DB_URL", "jdbc:external:users",
                "DB_USER", "external-user",
                "DB_PASSWORD", "external-test-value")));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:external:users");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("external-user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("external-test-value");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("Sin variables externas no se sustituye el datasource por H2 ni credenciales por defecto")
    void missingDatabaseVariablesHaveNoFallback() throws IOException {
        var environment = baseEnvironment();

        for (String property : new String[] {
                "spring.datasource.url", "spring.datasource.username", "spring.datasource.password"}) {
            assertThatThrownBy(() -> environment.getProperty(property))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Could not resolve placeholder");
        }
    }

    private StandardEnvironment baseEnvironment() throws IOException {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var loader = new YamlPropertySourceLoader();
        for (var source : loader.load("base", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
