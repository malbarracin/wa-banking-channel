package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalRequest;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.exception.BloqueoNoAplicableException;
import com.bcra.cards.ws.tarjeta.mapper.BloqueoMapper;
import com.bcra.cards.ws.tarjeta.repository.BloqueoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloqueoServiceTest {

    @Mock
    private CredencialesServiceClient credencialesClient;
    @Mock
    private TarjetaRepository tarjetaRepository;
    @Mock
    private BloqueoRepository bloqueoRepository;
    @Mock
    private AuditoriaTarjetaService auditoriaService;
    @Mock
    private BloqueoMapper bloqueoMapper;
    @InjectMocks
    private BloqueoService bloqueoService;

    private final UUID tarjetaId = UUID.randomUUID();
    private final ValidarCredencialResponse identidad = new ValidarCredencialResponse(
            true, "+5491100000000", "DNI", "12345678", Instant.now().plusSeconds(300));

    @Test
    @DisplayName("Confirmar cambia el estado y registra un bloqueo temporal del titular validado")
    void confirmaBloqueo() {
        when(credencialesClient.validar("sesion")).thenReturn(identidad);
        var tarjeta = new Tarjeta();
        tarjeta.setId(tarjetaId);
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        when(tarjetaRepository.buscarParaBloqueo(tarjetaId, "DNI", "12345678"))
                .thenReturn(Optional.of(tarjeta));

        bloqueoService.bloquearTemporal("sesion", tarjetaId,
                new BloqueoTemporalRequest(tarjetaId, "CONFIRMAR"));

        assertThat(tarjeta.getEstado()).isEqualTo(EstadoTarjeta.BLOQUEO_TEMPORAL);
        verify(tarjetaRepository).save(tarjeta);
        verify(bloqueoRepository).save(argThat(bloqueo -> bloqueo.getTipo() == TipoBloqueo.TEMPORAL
                && tarjetaId.equals(bloqueo.getTarjetaId()) && bloqueo.getFechaBloqueo() != null
                && identidad.numeroWhatsapp().equals(bloqueo.getUsuarioEjecutor())));
        verify(auditoriaService).registrarBloqueoTemporal(identidad, tarjetaId);
    }

    @Test
    @DisplayName("Sin confirmar el servicio rechaza antes de acceder a tarjetas y persistencia")
    void rechazaSinConfirmacion() {
        when(credencialesClient.validar("sesion")).thenReturn(identidad);
        assertThatThrownBy(() -> bloqueoService.bloquearTemporal("sesion", tarjetaId,
                new BloqueoTemporalRequest(tarjetaId, null))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tarjetaRepository, bloqueoRepository, auditoriaService);
    }

    @Test
    @DisplayName("Una tarjeta inactiva no genera bloqueo ni auditoría exitosa")
    void rechazaInactiva() {
        when(credencialesClient.validar("sesion")).thenReturn(identidad);
        var tarjeta = new Tarjeta();
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        when(tarjetaRepository.buscarParaBloqueo(tarjetaId, "DNI", "12345678"))
                .thenReturn(Optional.of(tarjeta));
        assertThatThrownBy(() -> bloqueoService.bloquearTemporal("sesion", tarjetaId,
                new BloqueoTemporalRequest(tarjetaId, "CONFIRMAR")))
                .isInstanceOf(BloqueoNoAplicableException.class);
        verifyNoInteractions(bloqueoRepository, auditoriaService);
    }

    @Test
    @DisplayName("Una credencial vacía no invoca H2 ni accede a productos")
    void rechazaCredencialVacia() {
        assertThatThrownBy(() -> bloqueoService.bloquearTemporal(" ", tarjetaId,
                new BloqueoTemporalRequest(tarjetaId, "CONFIRMAR"))).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(credencialesClient, tarjetaRepository, bloqueoRepository, auditoriaService);
    }
}
