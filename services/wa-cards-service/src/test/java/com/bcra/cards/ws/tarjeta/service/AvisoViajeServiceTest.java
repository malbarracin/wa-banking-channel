package com.bcra.cards.ws.tarjeta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeRequest;
import com.bcra.cards.ws.tarjeta.entity.AvisoViaje;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.bcra.cards.ws.tarjeta.mapper.AvisoViajeMapper;
import com.bcra.cards.ws.tarjeta.repository.AvisoViajeRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AvisoViajeServiceTest {

    @Mock
    private CredencialesServiceClient credenciales;
    @Mock
    private TarjetaRepository tarjetas;
    @Mock
    private AvisoViajeRepository avisos;

    private AvisoViajeService servicio;
    private final UUID tarjetaId = UUID.randomUUID();
    private final LocalDate fecha = LocalDate.of(2027, 1, 10);

    @BeforeEach
    void preparar() {
        servicio = new AvisoViajeService(credenciales, tarjetas, avisos,
                Mappers.getMapper(AvisoViajeMapper.class));
        ReflectionTestUtils.setField(servicio, "recomendaciones", "Conservá el contacto oficial del banco.");
    }

    @Test
    @DisplayName("Persiste un aviso confirmado y devuelve la recomendación configurada")
    void registraAvisoConfirmado() {
        autorizar();
        when(tarjetas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(tarjetaId, "DNI", "10000001"))
                .thenReturn(Optional.of(new Tarjeta()));
        var respuesta = servicio.registrar("sesion", tarjetaId, solicitud(fecha, fecha.plusDays(10)));
        var captor = org.mockito.ArgumentCaptor.forClass(AvisoViaje.class);
        verify(avisos).save(captor.capture());
        assertThat(captor.getValue().isConfirmado()).isTrue();
        assertThat(captor.getValue().getFechaRegistro()).isNotNull();
        assertThat(respuesta.tarjetaId()).isEqualTo(tarjetaId);
        assertThat(respuesta.mensajeConfirmacion()).contains("Conservá el contacto oficial del banco.",
                "políticas internacionales del emisor", "límites del banco");
    }

    @Test
    @DisplayName("Credenciales ausentes o vacías no consultan H2 ni repositorios")
    void rechazaCredencialAusente() {
        for (String credencial : new String[] {null, "", " "}) {
            assertThatThrownBy(() -> servicio.registrar(credencial, tarjetaId, solicitud(fecha, fecha)))
                    .isInstanceOf(UnauthorizedException.class);
        }
        verifyNoInteractions(credenciales, tarjetas, avisos);
    }

    @Test
    @DisplayName("Una tarjeta ajena no genera un aviso")
    void rechazaTarjetaAjena() {
        autorizar();
        assertThatThrownBy(() -> servicio.registrar("sesion", tarjetaId, solicitud(fecha, fecha)))
                .isInstanceOf(TarjetaNoEncontradaException.class);
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("Fechas ausentes o invertidas no consultan tarjetas ni persisten")
    void rechazaFechasInvalidas() {
        autorizar();
        for (AvisoViajeRequest request : new AvisoViajeRequest[] {
                solicitud(null, fecha), solicitud(fecha, null), solicitud(fecha, fecha.minusDays(1))}) {
            assertThatThrownBy(() -> servicio.registrar("sesion", tarjetaId, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(tarjetas, avisos);
    }

    private void autorizar() {
        when(credenciales.validar("sesion")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000001", "DNI", "10000001", Instant.now().plusSeconds(600)));
    }

    private AvisoViajeRequest solicitud(LocalDate desde, LocalDate hasta) {
        return new AvisoViajeRequest(tarjetaId, "España", null, desde, hasta);
    }
}
