package com.bcra.insurance.ws.seguro.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;

import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;

import static org.assertj.core.api.Assertions.assertThat;

class SiniestroMapperTest {

    private final SiniestroMapper mapper = Mappers.getMapper(SiniestroMapper.class);

    @Test
    @DisplayName("Proyecta número de caso y próximos pasos de la denuncia")
    void proyectaDenunciaResponse() {
        Siniestro siniestro = siniestro(EstadoSiniestro.RECIBIDO, null);

        DenunciaResponse respuesta = mapper.toDenunciaResponse(siniestro,
                "Enviá la documentación requerida por los canales oficiales del banco.",
                "Los plazos dependen del proceso formal correspondiente a tu ramo.");

        assertThat(respuesta.numeroCaso()).isEqualTo("SIN-1");
        assertThat(respuesta.proximosPasos()).contains("canales oficiales");
        assertThat(respuesta.plazos()).contains("proceso formal");
    }

    @Test
    @DisplayName("Conserva el texto de pendiente informado por el sistema de siniestros")
    void conservaPendienteDelSistema() {
        Siniestro siniestro = siniestro(EstadoSiniestro.EN_ANALISIS, "Esperá el análisis");

        SiniestroResponse respuesta = mapper.toResponse(siniestro);

        assertThat(respuesta.numeroCaso()).isEqualTo("SIN-1");
        assertThat(respuesta.estado()).isEqualTo(EstadoSiniestro.EN_ANALISIS);
        assertThat(respuesta.pendiente()).isEqualTo("Esperá el análisis");
    }

    @ParameterizedTest
    @CsvSource({
            "RECIBIDO, Sin acciones pendientes del cliente",
            "EN_ANALISIS, Sin acciones pendientes del cliente",
            "DOCUMENTACION_PENDIENTE, Documentación pendiente",
            "RESUELTO, Ninguna"
    })
    @DisplayName("Deriva pendiente del estado cuando el sistema no informó texto")
    void derivaPendienteDelEstado(EstadoSiniestro estado, String pendienteEsperada) {
        Siniestro siniestro = siniestro(estado, null);

        assertThat(mapper.toResponse(siniestro).pendiente()).isEqualTo(pendienteEsperada);
    }

    @Test
    @DisplayName("Trata pendiente en blanco como ausencia y deriva del estado")
    void derivaCuandoPendienteEstaEnBlanco() {
        Siniestro siniestro = siniestro(EstadoSiniestro.DOCUMENTACION_PENDIENTE, "   ");

        assertThat(mapper.toResponse(siniestro).pendiente()).isEqualTo("Documentación pendiente");
    }

    private static Siniestro siniestro(EstadoSiniestro estado, String pendiente) {
        Instant ahora = Instant.now();
        return Siniestro.builder().numeroCaso("SIN-1").polizaId(UUID.randomUUID())
                .tipoSiniestro(TipoSiniestro.HOGAR).fechaSiniestro(LocalDate.now().minusDays(1))
                .descripcion("Daño").estado(estado).pendiente(pendiente).documentosPendientes(List.of())
                .fechaApertura(ahora).ventanaDuplicadoHasta(ahora.plusSeconds(3600)).build();
    }
}
