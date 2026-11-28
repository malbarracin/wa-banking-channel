package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.service.AuditService;
import com.bcra.channel.ws.binding.dto.VerifyIdentityRequest;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingConflictException;
import com.bcra.channel.ws.binding.mapper.BindingMapper;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;
import com.bcra.channel.ws.binding.service.BindingService;
import com.bcra.channel.ws.binding.service.BindingStateMachine;
import com.bcra.channel.ws.binding.service.IdentityVerifierResolver;
import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.client.UserLookupStatus;
import com.bcra.channel.ws.client.UsersServiceClient;
import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.ChannelMessages;

@ExtendWith(MockitoExtension.class)
class BindingServiceVerifyTest {

    private static final String NUMBER = "+5491112345678";
    private static final UUID CLIENT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

    @Mock
    private BindingRepository bindingRepository;
    @Mock
    private VerificationAttemptRepository verificationAttemptRepository;
    @Mock
    private BindingMapper bindingMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private ChannelMessages channelMessages;
    @Mock
    private UsersServiceClient usersServiceClient;
    @Mock
    private IdentityVerifierResolver identityVerifierResolver;

    private BankChannelProperties properties;
    private BindingService bindingService;

    @BeforeEach
    void setUp() {
        properties = new BankChannelProperties();
        properties.getVerification().setMaxAttempts(3);
        properties.getVerification().setWindowMinutes(60);
        bindingService = new BindingService(
                bindingRepository,
                verificationAttemptRepository,
                bindingMapper,
                new BindingStateMachine(),
                auditService,
                channelMessages,
                properties,
                usersServiceClient,
                identityVerifierResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Anti-fraude: número ACTIVO con otro clientId → CONFLICT mensaje 8.7 y ANTIFRAUD_CHECK ERROR")
    void antifraudRejectsActiveNumberForOtherClient() {
        Binding pending = pendingBinding();
        when(bindingRepository.findByWhatsappNumberForUpdate(NUMBER)).thenReturn(Optional.of(pending));
        when(verificationAttemptRepository.countByBindingIdAndSuccessFalseAndAttemptedAtGreaterThanEqual(
                        eq(BINDING_ID), any()))
                .thenReturn(0L);
        when(usersServiceClient.findById(CLIENT_ID)).thenReturn(activeLookup());
        when(identityVerifierResolver.verify(any(), eq("ok"))).thenReturn(true);
        when(bindingRepository.existsByWhatsappNumberAndStatusAndClientIdNot(
                        NUMBER, BindingStatus.ACTIVO, CLIENT_ID))
                .thenReturn(true);
        when(channelMessages.numberAlreadyLinked()).thenReturn("mensaje-8.7");

        assertThatThrownBy(() -> bindingService.verify(NUMBER, new VerifyIdentityRequest(CLIENT_ID, "ok")))
                .isInstanceOf(BindingConflictException.class)
                .hasMessage("mensaje-8.7");

        verify(auditService).record(
                eq(BINDING_ID),
                eq(NUMBER),
                eq(AuditAction.ANTIFRAUD_CHECK),
                eq(AuditService.RESULT_ERROR),
                eq(BindingService.ACTOR_SISTEMA),
                any());
        verify(bindingRepository, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
    }

    @Test
    @DisplayName("Sin vínculo / no PENDIENTE → CONFLICT y no consulta H1")
    void withoutPendingDoesNotCallH1() {
        when(bindingRepository.findByWhatsappNumberForUpdate(NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bindingService.verify(NUMBER, new VerifyIdentityRequest(CLIENT_ID, "ok")))
                .isInstanceOf(BindingConflictException.class);

        verify(usersServiceClient, never()).findById(any());
    }

    private static Binding pendingBinding() {
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(NUMBER);
        binding.setStatus(BindingStatus.PENDIENTE_VERIFICACION);
        binding.setTermsAcceptedAt(NOW);
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(NOW);
        binding.setUpdatedAt(NOW);
        return binding;
    }

    private static UserLookupResult activeLookup() {
        return new UserLookupResult(
                UserLookupStatus.FOUND_ACTIVE,
                CLIENT_ID,
                "USR-1",
                "Ana Pérez",
                "ana@example.com",
                NUMBER);
    }
}
