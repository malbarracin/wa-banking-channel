package com.bcra.channel.ws.config;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificacionConfigTest {

    @Test
    @DisplayName("Sin método aprobado por Compliance ninguna prueba aportada por el cliente autoriza la identidad")
    void rechazaPruebasPorDefecto() {
        var verificador = new VerificacionConfig().verificadorIdentidad();

        assertThat(verificador.esIdentidadValida("DNI", "12345678", Map.of("exitosa", "true"))).isFalse();
    }
}
