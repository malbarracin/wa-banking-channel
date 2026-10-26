package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.bcra.channel.ws.vinculo.client.ClienteEstadoResponse;
import com.bcra.channel.ws.vinculo.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.dto.VerificarIdentidadRequest;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.exception.ClienteNoEncontradoException;
import com.bcra.channel.ws.vinculo.exception.ClienteNoHabilitadoException;
import com.bcra.channel.ws.vinculo.exception.NumeroYaVinculadoException;
import com.bcra.channel.ws.vinculo.exception.VerificacionPausadaException;
import com.bcra.channel.ws.vinculo.exception.VinculoNoEncontradoException;
import com.bcra.channel.ws.vinculo.mapper.VerificacionMapper;
import com.bcra.channel.ws.vinculo.repository.IntentoVerificacionRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacionServiceTest {

    private static final String NUMERO = "+5491112345678";
    private static final VerificarIdentidadRequest REQUEST =
            new VerificarIdentidadRequest("DNI", "12345678", Map.of("prueba", "secreto"));

    @Mock private VinculoRepository vinculos;
    @Mock private IntentoVerificacionRepository intentos;
    @Mock private UsersServiceClient users;
    @Mock private VerificadorIdentidad verificador;
    @Mock private VinculoAuditService auditoria;
    private VerificacionService service;
    private Vinculo pendiente;

    @BeforeEach
    void preparar() {
        service = new VerificacionService(vinculos, intentos, users, verificador, auditoria,
                Mappers.getMapper(VerificacionMapper.class));
        pendiente = vinculo(VinculoEstado.PENDIENTE_VERIFICACION);
    }

    private Vinculo vinculo(VinculoEstado estado) {
        Vinculo vinculo = new Vinculo();
        vinculo.setId(UUID.randomUUID());
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setEstado(estado);
        vinculo.setTerminosAceptados(true);
        return vinculo;
    }

    private void habilitar() {
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of(pendiente));
        when(users.consultarEstado("DNI", "12345678")).thenReturn(new ClienteEstadoResponse(EstadoCliente.ACTIVE));
    }

    @Test
    @DisplayName("Activa el vínculo, registra un intento nuevo y entrega el resultado verificable")
    void activaConIdentidadValida() {
        habilitar();
        when(verificador.esIdentidadValida("DNI", "12345678", REQUEST.datosVerificacion())).thenReturn(true);

        var respuesta = service.verificar(NUMERO, REQUEST);

        assertThat(respuesta.exitosa()).isTrue();
        assertThat(respuesta.vinculoId()).isEqualTo(pendiente.getId());
        assertThat(respuesta.estadoVinculo()).isEqualTo(VinculoEstado.ACTIVO);
        assertThat(respuesta.verificadoEn()).isNotNull();
        assertThat(respuesta.intentosRestantes()).isNull();
        verificarAuditoria(AuditAccion.VINCULACION, AuditResultado.EXITOSO);
        ArgumentCaptor<IntentoVerificacion> captura = ArgumentCaptor.forClass(IntentoVerificacion.class);
        verify(intentos).saveAndFlush(captura.capture());
        assertThat(captura.getValue().getResultado()).isEqualTo(ResultadoIntento.EXITOSO);
        assertThat(captura.getValue().getId()).isNull();
        assertThat(REQUEST.toString()).doesNotContain("secreto", "12345678");
        assertThat(respuesta.toString()).doesNotContain("12345678");
    }

    @Test
    @DisplayName("Un fallo conserva el pendiente e informa intentos restantes")
    void conservaPendienteTrasFallo() {
        habilitar();
        when(intentos.countByVinculoIdAndResultadoAndFechaAfter(eq(pendiente.getId()),
                eq(ResultadoIntento.FALLIDO), any(Instant.class))).thenReturn(1L);

        var respuesta = service.verificar(NUMERO, REQUEST);

        assertThat(respuesta.exitosa()).isFalse();
        assertThat(respuesta.intentosRestantes()).isEqualTo(1);
        assertThat(respuesta.verificadoEn()).isNull();
        assertThat(pendiente.getEstado()).isEqualTo(VinculoEstado.PENDIENTE_VERIFICACION);
        verificarAuditoria(AuditAccion.VERIFICACION_FALLIDA, AuditResultado.RECHAZADO);
    }

    @Test
    @DisplayName("Agotar la ventana pausa la verificación sin evaluar nuevas pruebas")
    void pausaAlAgotarIntentos() {
        habilitar();
        when(intentos.countByVinculoIdAndResultadoAndFechaAfter(eq(pendiente.getId()),
                eq(ResultadoIntento.FALLIDO), any(Instant.class))).thenReturn(3L);

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST))
                .isInstanceOf(VerificacionPausadaException.class).hasMessageContaining("soporte");
        verifyNoInteractions(verificador);
        assertThat(pendiente.getEstado()).isEqualTo(VinculoEstado.PENDIENTE_VERIFICACION);
    }

    @Test
    @DisplayName("Sin aceptación previa no consulta H1 ni verifica identidad")
    void rechazaSinTerminos() {
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST))
                .isInstanceOf(VinculoNoEncontradoException.class);
        verifyNoInteractions(users, verificador, intentos);
    }

    @Test
    @DisplayName("Traduce el cliente inexistente de H1 al error de dominio 404")
    void rechazaClienteInexistente() {
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of(pendiente));
        when(users.consultarEstado("DNI", "12345678")).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not found", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST))
                .isInstanceOf(ClienteNoEncontradoException.class);
        verifyNoInteractions(verificador, intentos);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCliente.class, names = {"SUSPENDED", "LOGICAL_DELETE"})
    @DisplayName("Un cliente suspendido o dado de baja no puede activar el canal")
    void rechazaClienteInactivo(EstadoCliente estado) {
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of(pendiente));
        when(users.consultarEstado("DNI", "12345678")).thenReturn(new ClienteEstadoResponse(estado));

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST))
                .isInstanceOf(ClienteNoHabilitadoException.class);
        verifyNoInteractions(verificador, intentos);
    }

    @Test
    @DisplayName("Un número activo rechaza el nuevo vínculo y audita la derivación a soporte")
    void rechazaNumeroOcupado() {
        habilitar();
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of(pendiente, vinculo(VinculoEstado.ACTIVO)));

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST))
                .isInstanceOf(NumeroYaVinculadoException.class);
        verificarAuditoria(AuditAccion.VINCULACION, AuditResultado.RECHAZADO);
        verifyNoInteractions(verificador, intentos);
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"BLOQUEADO", "DESVINCULADO"})
    @DisplayName("La revinculación verifica de nuevo y no cuenta intentos del vínculo cerrado")
    void revinculaSinReutilizarIntentos(VinculoEstado estado) {
        habilitar();
        Vinculo anterior = vinculo(estado);
        anterior.setDocumentoTipo("DNI");
        anterior.setDocumentoNumero("12345678");
        when(vinculos.bloquearPorNumero(NUMERO)).thenReturn(List.of(anterior, pendiente));
        when(verificador.esIdentidadValida("DNI", "12345678", REQUEST.datosVerificacion())).thenReturn(true);

        assertThat(service.verificar(NUMERO, REQUEST).exitosa()).isTrue();

        assertThat(anterior.getEstado()).isEqualTo(estado);
        verificarAuditoria(AuditAccion.REVINCULACION, AuditResultado.EXITOSO);
        verify(intentos).countByVinculoIdAndResultadoAndFechaAfter(eq(pendiente.getId()),
                eq(ResultadoIntento.FALLIDO), any(Instant.class));
    }

    @Test
    @DisplayName("El índice único protege una carrera de activación y se traduce a conflicto")
    void traduceCarreraDeUnicidad() {
        habilitar();
        when(verificador.esIdentidadValida("DNI", "12345678", REQUEST.datosVerificacion())).thenReturn(true);
        when(vinculos.saveAndFlush(pendiente)).thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> service.verificar(NUMERO, REQUEST)).isInstanceOf(NumeroYaVinculadoException.class);
        verificarAuditoria(AuditAccion.VINCULACION, AuditResultado.RECHAZADO);
    }

    private void verificarAuditoria(AuditAccion accion, AuditResultado resultado) {
        verify(auditoria).registrar(accion, resultado, NUMERO, "DNI", "12345678", null);
    }
}
