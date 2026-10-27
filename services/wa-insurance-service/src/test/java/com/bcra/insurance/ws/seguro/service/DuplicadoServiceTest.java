package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroDuplicadoException;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicadoServiceTest {

    private static final int VENTANA_DIAS = 30;

    @Mock
    private SiniestroRepository siniestros;

    private DuplicadoService duplicados;

    @BeforeEach
    void setUp() {
        duplicados = new DuplicadoService(siniestros, VENTANA_DIAS);
    }

    @Test
    @DisplayName("Permite abrir cuando no hay denuncias abiertas coincidentes en la ventana")
    void permiteSinDuplicado() {
        UUID polizaId = UUID.randomUUID();
        when(siniestros.findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
                eq(polizaId), eq(TipoSiniestro.HOGAR), any(), any())).thenReturn(List.of());

        duplicados.asegurarSinDuplicado(polizaId, TipoSiniestro.HOGAR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EstadoSiniestro>> estados = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(siniestros).findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
                eq(polizaId), eq(TipoSiniestro.HOGAR), estados.capture(), limite.capture());
        assertThat(estados.getValue()).containsExactlyInAnyOrderElementsOf(EnumSet.of(
                EstadoSiniestro.RECIBIDO, EstadoSiniestro.EN_ANALISIS, EstadoSiniestro.DOCUMENTACION_PENDIENTE));
        assertThat(limite.getValue()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("Rechaza un duplicado abierto de la misma póliza y tipo dentro de la ventana")
    void rechazaDuplicado() {
        UUID polizaId = UUID.randomUUID();
        when(siniestros.findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
                eq(polizaId), eq(TipoSiniestro.AUTO), any(), any()))
                .thenReturn(List.of(Siniestro.builder()
                        .numeroCaso("SIN-EXISTENTE")
                        .polizaId(polizaId)
                        .tipoSiniestro(TipoSiniestro.AUTO)
                        .fechaSiniestro(java.time.LocalDate.now())
                        .descripcion("previo")
                        .estado(EstadoSiniestro.RECIBIDO)
                        .fechaApertura(Instant.now())
                        .ventanaDuplicadoHasta(Instant.now().plusSeconds(3600))
                        .build()));

        assertThatThrownBy(() -> duplicados.asegurarSinDuplicado(polizaId, TipoSiniestro.AUTO))
                .isInstanceOf(SiniestroDuplicadoException.class)
                .hasMessageContaining("denuncia abierta");
    }

    @Test
    @DisplayName("Calcula el fin de ventana a partir de la apertura y los días configurados")
    void calculaVentanaHasta() {
        Instant apertura = Instant.parse("2026-10-01T12:00:00Z");

        assertThat(duplicados.ventanaHasta(apertura)).isEqualTo(Instant.parse("2026-10-31T12:00:00Z"));
    }
}
