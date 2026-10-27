package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.insurance.ws.seguro.dto.DocumentacionPendienteResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoEncontradoException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentacionServiceTest {

    @Mock
    private SiniestroService siniestros;
    @InjectMocks
    private DocumentacionService service;

    @Test
    @DisplayName("Lista documentos pendientes y orienta el envío formal")
    void listaDocumentosPendientes() {
        Instant ahora = Instant.now();
        Siniestro siniestro = Siniestro.builder().numeroCaso("SIN-1").polizaId(UUID.randomUUID())
                .tipoSiniestro(TipoSiniestro.AUTO).fechaSiniestro(LocalDate.now())
                .descripcion("Choque").estado(EstadoSiniestro.DOCUMENTACION_PENDIENTE)
                .documentosPendientes(List.of("DNI", "Denuncia policial")).fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(3600)).build();
        when(siniestros.obtenerPropio("valida", "SIN-1")).thenReturn(siniestro);

        DocumentacionPendienteResponse respuesta = service.listarPendiente("valida", "SIN-1");

        assertThat(respuesta.documentos()).containsExactly("DNI", "Denuncia policial");
        assertThat(respuesta.orientacionEnvio()).contains("canales oficiales");
        assertThat(respuesta.orientacionEnvio()).contains("no reemplaza");
    }

    @Test
    @DisplayName("Propaga el error si el caso no existe")
    void propagaCasoInexistente() {
        when(siniestros.obtenerPropio("valida", "SIN-X")).thenThrow(new SiniestroNoEncontradoException());

        assertThatThrownBy(() -> service.listarPendiente("valida", "SIN-X"))
                .isInstanceOf(SiniestroNoEncontradoException.class);
    }

    @Test
    @DisplayName("Devuelve lista vacía si el sistema no informó documentos pendientes")
    void listaVaciaSinDocumentosInformados() {
        Instant ahora = Instant.now();
        Siniestro siniestro = Siniestro.builder().numeroCaso("SIN-2").polizaId(UUID.randomUUID())
                .tipoSiniestro(TipoSiniestro.HOGAR).fechaSiniestro(LocalDate.now())
                .descripcion("Daño").estado(EstadoSiniestro.EN_ANALISIS)
                .documentosPendientes(null).fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(3600)).build();
        siniestro.setDocumentosPendientes(null);
        when(siniestros.obtenerPropio("valida", "SIN-2")).thenReturn(siniestro);

        DocumentacionPendienteResponse respuesta = service.listarPendiente("valida", "SIN-2");

        assertThat(respuesta.documentos()).isEmpty();
        assertThat(respuesta.orientacionEnvio()).contains("canales oficiales");
    }
}
