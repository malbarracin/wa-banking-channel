package com.bcra.channel.ws.vinculo.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.exception.NumeroYaVinculadoException;
import com.bcra.channel.ws.vinculo.mapper.VinculoMapper;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AceptarTerminosConcurrenciaTest {

    private static final String NUMERO = "+5491112345678";

    @Mock
    private VinculoRepository vinculoRepository;
    @Mock
    private VinculoMapper vinculoMapper;
    @Mock
    private VinculoAuditService vinculoAuditService;
    @Mock
    private UsersServiceClient usersServiceClient;
    @Mock
    private CredencialesServiceClient credencialesServiceClient;
    @InjectMocks
    private VinculoService vinculoService;

    @Test
    @DisplayName("Rechaza con conflicto el pendiente concurrente detectado por el índice único")
    void rechazaDuplicadoConcurrenteSinExponerErrorDeBase() {
        when(vinculoRepository.bloquearPorNumero(NUMERO)).thenReturn(List.of());
        when(vinculoRepository.saveAndFlush(any(Vinculo.class)))
                .thenThrow(new DataIntegrityViolationException("uq_vinculo_numero_pendiente"));

        assertThatThrownBy(() -> vinculoService.aceptarTerminos(NUMERO))
                .isInstanceOf(NumeroYaVinculadoException.class);

        verify(vinculoAuditService).registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.RECHAZADO,
                NUMERO, null, null, null);
        verifyNoInteractions(vinculoMapper, usersServiceClient, credencialesServiceClient);
    }
}
