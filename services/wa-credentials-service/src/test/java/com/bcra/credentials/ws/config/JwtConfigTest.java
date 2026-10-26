package com.bcra.credentials.ws.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JwtConfigTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 31})
    @DisplayName("Rechaza claves JWT de menos de 32 bytes sin revelar su contenido")
    void rechazaClaveInsuficiente(int longitud) {
        String clave = "x".repeat(longitud);

        assertThatIllegalArgumentException().isThrownBy(() -> new JwtConfig().jwtEncoder(clave))
                .withMessage("La clave JWT debe tener al menos 32 bytes");
    }

    @Test
    @DisplayName("Acepta una clave JWT en el límite de 32 bytes")
    void aceptaClaveEnLimite() {
        assertThat(new JwtConfig().jwtEncoder("x".repeat(32))).isNotNull();
    }
}
