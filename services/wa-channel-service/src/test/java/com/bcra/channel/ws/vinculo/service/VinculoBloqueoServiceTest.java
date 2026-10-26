package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.dto.BloquearVinculoRequest;
import com.bcra.channel.ws.vinculo.dto.DesvincularRequest;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.exception.CredencialInvalidaException;
import com.bcra.channel.ws.vinculo.exception.VinculoEstadoConflictoException;
import com.bcra.channel.ws.vinculo.exception.VinculoNotFoundException;
import com.bcra.channel.ws.vinculo.mapper.VinculoMapper;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VinculoService — bloqueo y desvinculación")
class VinculoBloqueoServiceTest {

    private static final String NUMERO = "+5491112345678";

    @Mock private VinculoRepository vinculoRepository;
    @Mock private VinculoAuditService vinculoAuditService;
    @Mock private UsersServiceClient usersServiceClient;
    @Mock private CredencialesServiceClient credencialesServiceClient;

    private VinculoService service;
    private Vinculo activo;

    @BeforeEach
    void preparar() {
        service = new VinculoService(vinculoRepository, Mappers.getMapper(VinculoMapper.class),
                vinculoAuditService, usersServiceClient, credencialesServiceClient);
        activo = vinculo(VinculoEstado.ACTIVO);
    }

    @Test
    @DisplayName("Bloqueo por SOLICITUD_CLIENTE con credencial valida deja BLOQUEADO y pide revocación")
    void bloqueoPorCliente() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(activo));
        when(vinculoRepository.saveAndFlush(activo)).thenReturn(activo);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE")).thenReturn(true);

        VinculoResponse respuesta = service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.SOLICITUD_CLIENTE, true), "true");

        assertThat(respuesta.estado()).isEqualTo(VinculoEstado.BLOQUEADO);
        assertThat(activo.getEstado()).isEqualTo(VinculoEstado.BLOQUEADO);
        assertThat(activo.getMotivoBloqueo()).isEqualTo(MotivoBloqueo.SOLICITUD_CLIENTE);
        verify(vinculoAuditService).registrar(eq(AuditAccion.BLOQUEO), eq(AuditResultado.EXITOSO),
                eq(NUMERO), eq("DNI"), eq("12345678"), eq("SOLICITUD_CLIENTE"));
        verify(credencialesServiceClient).solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE");
        verify(vinculoAuditService, never()).registrar(eq(AuditAccion.BLOQUEO), eq(AuditResultado.RECHAZADO),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bloqueo por POLITICA_BANCO no exige credencial del cliente")
    void bloqueoPorPoliticaSinCredencial() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(activo));
        when(vinculoRepository.saveAndFlush(activo)).thenReturn(activo);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "POLITICA_BANCO")).thenReturn(true);

        VinculoResponse respuesta = service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.POLITICA_BANCO, true), null);

        assertThat(respuesta.estado()).isEqualTo(VinculoEstado.BLOQUEADO);
        assertThat(activo.getMotivoBloqueo()).isEqualTo(MotivoBloqueo.POLITICA_BANCO);
        verify(credencialesServiceClient).solicitarRevocacion(NUMERO, "POLITICA_BANCO");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "FALSE", "1"})
    @DisplayName("Bloqueo SOLICITUD_CLIENTE sin credencial válida → CredencialInvalidaException")
    void bloqueoClienteSinCredencial(String header) {
        assertThatThrownBy(() -> service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.SOLICITUD_CLIENTE, true), header))
                .isInstanceOf(CredencialInvalidaException.class);
        verify(vinculoRepository, never()).saveAndFlush(any());
        verify(credencialesServiceClient, never()).solicitarRevocacion(any(), any());
    }

    @Test
    @DisplayName("Bloqueo de vínculo inexistente → VinculoNotFoundException")
    void bloqueoSinVinculo() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.POLITICA_BANCO, true), null))
                .isInstanceOf(VinculoNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"ACTIVO"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Bloqueo con estado distinto de ACTIVO → conflicto")
    void bloqueoEstadoInvalido(VinculoEstado estado) {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(vinculo(estado)));

        assertThatThrownBy(() -> service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.POLITICA_BANCO, true), null))
                .isInstanceOf(VinculoEstadoConflictoException.class);
        verify(credencialesServiceClient, never()).solicitarRevocacion(any(), any());
    }

    @Test
    @DisplayName("Desvinculación con credencial deja DESVINCULADO y pide revocación")
    void desvinculacionExitosa() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(activo));
        when(vinculoRepository.saveAndFlush(activo)).thenReturn(activo);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE")).thenReturn(true);

        VinculoResponse respuesta = service.desvincular(NUMERO, new DesvincularRequest(true), "true");

        assertThat(respuesta.estado()).isEqualTo(VinculoEstado.DESVINCULADO);
        verify(vinculoAuditService).registrar(eq(AuditAccion.DESVINCULACION), eq(AuditResultado.EXITOSO),
                eq(NUMERO), eq("DNI"), eq("12345678"), isNull());
        verify(credencialesServiceClient).solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE");
    }

    @Test
    @DisplayName("Desvinculación admite vínculo previamente BLOQUEADO")
    void desvinculacionDesdeBloqueado() {
        Vinculo bloqueado = vinculo(VinculoEstado.BLOQUEADO);
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(bloqueado));
        when(vinculoRepository.saveAndFlush(bloqueado)).thenReturn(bloqueado);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE")).thenReturn(true);

        assertThat(service.desvincular(NUMERO, new DesvincularRequest(true), "true").estado())
                .isEqualTo(VinculoEstado.DESVINCULADO);
    }

    @Test
    @DisplayName("Si H2 falla el bloqueo local queda y se audita el rechazo de la revocación")
    void bloqueoConFalloDeH2() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(activo));
        when(vinculoRepository.saveAndFlush(activo)).thenReturn(activo);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "POLITICA_BANCO")).thenReturn(false);

        VinculoResponse respuesta = service.bloquear(NUMERO,
                new BloquearVinculoRequest(MotivoBloqueo.POLITICA_BANCO, true), null);

        assertThat(respuesta.estado()).isEqualTo(VinculoEstado.BLOQUEADO);
        verify(vinculoAuditService).registrar(eq(AuditAccion.BLOQUEO), eq(AuditResultado.EXITOSO),
                eq(NUMERO), eq("DNI"), eq("12345678"), eq("POLITICA_BANCO"));
        verify(vinculoAuditService).registrar(eq(AuditAccion.BLOQUEO), eq(AuditResultado.RECHAZADO),
                eq(NUMERO), eq("DNI"), eq("12345678"), eq("Fallo solicitud revocacion H2"));
    }

    @Test
    @DisplayName("Si H2 falla la desvinculación local queda y se audita el rechazo")
    void desvinculacionConFalloDeH2() {
        when(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .thenReturn(Optional.of(activo));
        when(vinculoRepository.saveAndFlush(activo)).thenReturn(activo);
        when(credencialesServiceClient.solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE")).thenReturn(false);

        assertThat(service.desvincular(NUMERO, new DesvincularRequest(true), "true").estado())
                .isEqualTo(VinculoEstado.DESVINCULADO);
        verify(vinculoAuditService).registrar(eq(AuditAccion.DESVINCULACION), eq(AuditResultado.RECHAZADO),
                eq(NUMERO), eq("DNI"), eq("12345678"), eq("Fallo solicitud revocacion H2"));
    }

    private Vinculo vinculo(VinculoEstado estado) {
        Vinculo vinculo = new Vinculo();
        vinculo.setId(UUID.randomUUID());
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("12345678");
        vinculo.setEstado(estado);
        vinculo.setFechaUltimoCambioEstado(Instant.parse("2026-01-02T10:00:00Z"));
        return vinculo;
    }
}
