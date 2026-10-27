package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroDuplicadoException;
import com.bcra.insurance.ws.seguro.mapper.SiniestroMapper;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiniestroServiceTest {

    @Mock
    private PolizaService polizas;
    @Mock
    private DuplicadoService duplicados;
    @Mock
    private SiniestroRepository siniestros;
    @Mock
    private SiniestroMapper mapper;

    @InjectMocks
    private SiniestroService service;

    @Test
    @DisplayName("Abre el caso con número único, estado recibido y próximos pasos")
    void abreCasoExitoso() {
        UUID polizaId = UUID.randomUUID();
        Poliza poliza = Poliza.builder().numeroPoliza("POL-1").ramo("HOGAR").vigente(true)
                .titularDocumentoTipo("DNI").titularDocumentoNumero("12345678")
                .fechaVigenciaDesde(LocalDate.now().minusDays(1))
                .fechaVigenciaHasta(LocalDate.now().plusDays(1)).build();
        setId(poliza, polizaId);
        DenunciaRequest request = new DenunciaRequest(polizaId, TipoSiniestro.HOGAR, LocalDate.of(2026, 10, 20),
                "Rotura de cañería");
        Instant hasta = Instant.parse("2026-11-26T00:00:00Z");
        when(polizas.obtenerAutorizada("valida", polizaId)).thenReturn(poliza);
        when(duplicados.ventanaHasta(any())).thenReturn(hasta);
        when(siniestros.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDenunciaResponse(any(), anyString(), anyString()))
                .thenAnswer(invocation -> new DenunciaResponse(
                        ((Siniestro) invocation.getArgument(0)).getNumeroCaso(),
                        invocation.getArgument(1), invocation.getArgument(2)));

        DenunciaResponse respuesta = service.denunciar("valida", request);

        ArgumentCaptor<Siniestro> capturado = ArgumentCaptor.forClass(Siniestro.class);
        verify(duplicados).asegurarSinDuplicado(polizaId, TipoSiniestro.HOGAR);
        verify(siniestros).save(capturado.capture());
        Siniestro guardado = capturado.getValue();
        assertThat(guardado.getNumeroCaso()).startsWith("SIN-").hasSize(36);
        assertThat(guardado.getEstado()).isEqualTo(EstadoSiniestro.RECIBIDO);
        assertThat(guardado.getPolizaId()).isEqualTo(polizaId);
        assertThat(guardado.getTipoSiniestro()).isEqualTo(TipoSiniestro.HOGAR);
        assertThat(guardado.getDescripcion()).isEqualTo("Rotura de cañería");
        assertThat(guardado.getVentanaDuplicadoHasta()).isEqualTo(hasta);
        assertThat(respuesta.numeroCaso()).isEqualTo(guardado.getNumeroCaso());
        assertThat(respuesta.proximosPasos()).contains("canales oficiales");
        assertThat(respuesta.plazos()).contains("proceso formal");
    }

    @Test
    @DisplayName("No persiste si el control de duplicados detecta conflicto")
    void noPersisteAnteDuplicado() {
        UUID polizaId = UUID.randomUUID();
        Poliza poliza = Poliza.builder().numeroPoliza("POL-1").ramo("AUTO").vigente(true)
                .titularDocumentoTipo("DNI").titularDocumentoNumero("12345678")
                .fechaVigenciaDesde(LocalDate.now().minusDays(1))
                .fechaVigenciaHasta(LocalDate.now().plusDays(1)).build();
        setId(poliza, polizaId);
        when(polizas.obtenerAutorizada("valida", polizaId)).thenReturn(poliza);
        doThrow(new SiniestroDuplicadoException()).when(duplicados)
                .asegurarSinDuplicado(eq(polizaId), eq(TipoSiniestro.AUTO));

        assertThatThrownBy(() -> service.denunciar("valida",
                new DenunciaRequest(polizaId, TipoSiniestro.AUTO, LocalDate.now(), "Choque")))
                .isInstanceOf(SiniestroDuplicadoException.class);
        verify(siniestros, never()).save(any());
        verify(mapper, never()).toDenunciaResponse(any(), anyString(), anyString());
    }

    private static void setId(Poliza poliza, UUID id) {
        try {
            var campo = Poliza.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(poliza, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
