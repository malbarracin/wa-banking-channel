package com.bcra.cards.ws.tarjeta.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.mapper.ConsumoMapper;
import com.bcra.cards.ws.tarjeta.repository.ConsumoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsumoServiceTest {

    @Test
    @DisplayName("No consulta productos si falta la credencial o H2 rechaza la sesión")
    void autorizaAntesDeConsultarProductos() {
        var credenciales = mock(CredencialesServiceClient.class);
        var tarjetas = mock(TarjetaRepository.class);
        var consumos = mock(ConsumoRepository.class);
        var mapper = mock(ConsumoMapper.class);
        var service = new ConsumoService(credenciales, tarjetas, consumos, mapper, 90, 10);
        for (String credencial : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> service.listar(UUID.randomUUID(), credencial, null, null, null))
                    .isInstanceOf(UnauthorizedException.class);
        }
        verifyNoInteractions(credenciales, tarjetas, consumos, mapper);
        when(credenciales.validar("revocada")).thenThrow(new UnauthorizedException("Inválida"));
        assertThatThrownBy(() -> service.listar(UUID.randomUUID(), "revocada", null, null, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(tarjetas, consumos, mapper);
    }

    @Test
    @DisplayName("Rechaza una configuración que impida limitar las consultas")
    void rechazaConfiguracionInvalida() {
        assertThatThrownBy(() -> new ConsumoService(null, null, null, null, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsumoService(null, null, null, null, 90, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsumoService(null, null, null, null, 90, Integer.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
