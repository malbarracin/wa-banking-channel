package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.insurance.ws.exception.UnauthorizedException;
import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroDuplicadoException;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoAutorizadoException;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoEncontradoException;
import com.bcra.insurance.ws.seguro.mapper.SiniestroMapper;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;
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
    private PolizaService polizaService;
    @Mock
    private DuplicadoService duplicados;
    @Mock
    private CredencialesServiceClient credenciales;
    @Mock
    private SiniestroRepository siniestros;
    @Mock
    private PolizaRepository polizas;
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
        when(polizaService.obtenerAutorizada("valida", polizaId)).thenReturn(poliza);
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
        when(polizaService.obtenerAutorizada("valida", polizaId)).thenReturn(poliza);
        doThrow(new SiniestroDuplicadoException()).when(duplicados)
                .asegurarSinDuplicado(eq(polizaId), eq(TipoSiniestro.AUTO));

        assertThatThrownBy(() -> service.denunciar("valida",
                new DenunciaRequest(polizaId, TipoSiniestro.AUTO, LocalDate.now(), "Choque")))
                .isInstanceOf(SiniestroDuplicadoException.class);
        verify(siniestros, never()).save(any());
        verify(mapper, never()).toDenunciaResponse(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Devuelve el estado del sistema para un caso propio del titular")
    void consultaCasoPropio() {
        UUID polizaId = UUID.randomUUID();
        ValidarCredencialResponse identidad = identidad("DNI", "12345678");
        Poliza poliza = poliza("DNI", "12345678");
        Siniestro siniestro = siniestro(polizaId, EstadoSiniestro.EN_ANALISIS, "Esperá el análisis");
        SiniestroResponse esperado = new SiniestroResponse(siniestro.getNumeroCaso(), EstadoSiniestro.EN_ANALISIS,
                "Esperá el análisis");
        when(credenciales.validar("valida")).thenReturn(identidad);
        when(siniestros.findByNumeroCaso("SIN-1")).thenReturn(Optional.of(siniestro));
        when(polizas.findById(polizaId)).thenReturn(Optional.of(poliza));
        when(mapper.toResponse(siniestro)).thenReturn(esperado);

        assertThat(service.consultar("valida", "SIN-1")).isEqualTo(esperado);
        verify(credenciales).validar("valida");
    }

    @Test
    @DisplayName("Rechaza con 401 si la credencial no es válida")
    void rechazaSinCredencial() {
        when(credenciales.validar(null))
                .thenThrow(new UnauthorizedException("Verificá tu identidad para consultar tus seguros."));

        assertThatThrownBy(() -> service.consultar(null, "SIN-1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Informa 404 si el número de caso no existe")
    void informaCasoInexistente() {
        when(credenciales.validar("valida")).thenReturn(identidad("DNI", "12345678"));
        when(siniestros.findByNumeroCaso("SIN-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar("valida", "SIN-X"))
                .isInstanceOf(SiniestroNoEncontradoException.class);
    }

    @Test
    @DisplayName("Rechaza con 403 un caso ajeno sin revelar datos")
    void rechazaCasoAjeno() {
        UUID polizaId = UUID.randomUUID();
        ValidarCredencialResponse identidad = identidad("DNI", "12345678");
        Poliza ajena = poliza("DNI", "99999999");
        Siniestro siniestro = siniestro(polizaId, EstadoSiniestro.RECIBIDO, null);
        when(credenciales.validar("valida")).thenReturn(identidad);
        when(siniestros.findByNumeroCaso("SIN-1")).thenReturn(Optional.of(siniestro));
        when(polizas.findById(polizaId)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.consultar("valida", "SIN-1"))
                .isInstanceOf(SiniestroNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Un asegurado no titular no puede consultar el caso")
    void rechazaAseguradoNoTitular() {
        UUID polizaId = UUID.randomUUID();
        ValidarCredencialResponse identidad = identidad("DNI", "12345678");
        Poliza poliza = poliza("DNI", "otro");
        poliza.setAseguradoDocumentoTipo("DNI");
        poliza.setAseguradoDocumentoNumero("12345678");
        Siniestro siniestro = siniestro(polizaId, EstadoSiniestro.DOCUMENTACION_PENDIENTE, null);
        when(credenciales.validar("valida")).thenReturn(identidad);
        when(siniestros.findByNumeroCaso("SIN-1")).thenReturn(Optional.of(siniestro));
        when(polizas.findById(polizaId)).thenReturn(Optional.of(poliza));

        assertThatThrownBy(() -> service.obtenerPropio("valida", "SIN-1"))
                .isInstanceOf(SiniestroNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Informa 404 si la póliza asociada al caso ya no existe")
    void informaPolizaAsociadaInexistente() {
        UUID polizaId = UUID.randomUUID();
        Siniestro siniestro = siniestro(polizaId, EstadoSiniestro.RECIBIDO, null);
        when(credenciales.validar("valida")).thenReturn(identidad("DNI", "12345678"));
        when(siniestros.findByNumeroCaso("SIN-1")).thenReturn(Optional.of(siniestro));
        when(polizas.findById(polizaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar("valida", "SIN-1"))
                .isInstanceOf(SiniestroNoEncontradoException.class);
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

    private static ValidarCredencialResponse identidad(String tipo, String numero) {
        return new ValidarCredencialResponse(true, "+5491112345678", tipo, numero, Instant.now().plusSeconds(300));
    }

    private static Poliza poliza(String tipo, String numero) {
        return Poliza.builder().numeroPoliza("POL-1").ramo("HOGAR").vigente(true)
                .fechaVigenciaDesde(LocalDate.now().minusDays(10))
                .fechaVigenciaHasta(LocalDate.now().plusDays(10))
                .titularDocumentoTipo(tipo).titularDocumentoNumero(numero).build();
    }

    private static Siniestro siniestro(UUID polizaId, EstadoSiniestro estado, String pendiente) {
        Instant ahora = Instant.now();
        return Siniestro.builder().numeroCaso("SIN-1").polizaId(polizaId).tipoSiniestro(TipoSiniestro.HOGAR)
                .fechaSiniestro(LocalDate.now().minusDays(1)).descripcion("Daño").estado(estado)
                .pendiente(pendiente).documentosPendientes(List.of()).fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(3600)).build();
    }
}
